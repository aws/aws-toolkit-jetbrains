// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.isFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.ioDispatcher
import software.aws.toolkits.jetbrains.services.amazonq.CHAT_EXPLICIT_PROJECT_CONTEXT_TIMEOUT
import software.aws.toolkits.jetbrains.services.amazonq.SUPPLEMENTAL_CONTEXT_TIMEOUT
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.AmazonqTelemetry
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class ProjectContextProvider(val project: Project, private val encoderServer: EncoderServer, private val cs: CoroutineScope) : Disposable {
    private val retryCount = AtomicInteger(0)
    val isIndexComplete = AtomicBoolean(false)
    private val mapper = jacksonObjectMapper()

    // max number of requests that can be ongoing to an given server instance, excluding index()
    private val ioDispatcher = ioDispatcher(20)

    init {
        cs.launch {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                return@launch
            }

            // TODO: need better solution for this
            @Suppress("LoopWithTooManyJumpStatements")
            while (true) {
                if (encoderServer.isNodeProcessRunning()) {
                    delay(10000)
                    initAndIndex()
                    break
                } else {
                    delay(10000)
                }
            }
        }
    }

    data class FileCollectionResult(
        val files: List<String>,
        val fileSize: Int,
    )

    // TODO: move to LspMessage.kt
    data class Usage(
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("memoryUsage")
        val memoryUsage: Int? = null,
        @JsonProperty("cpuUsage")
        val cpuUsage: Int? = null,
    )

    // TODO: move to LspMessage.kt
    data class Chunk(
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("filePath")
        val filePath: String? = null,
        @JsonProperty("content")
        val content: String? = null,
        @JsonProperty("id")
        val id: String? = null,
        @JsonProperty("index")
        val index: String? = null,
        @JsonProperty("vec")
        val vec: List<String>? = null,
        @JsonProperty("context")
        val context: String? = null,
        @JsonProperty("prev")
        val prev: String? = null,
        @JsonProperty("next")
        val next: String? = null,
        @JsonProperty("relativePath")
        val relativePath: String? = null,
        @JsonProperty("programmingLanguage")
        val programmingLanguage: String? = null,
    )

    private suspend fun initAndIndex() {
        while (retryCount.get() < 5) {
            try {
                logger.info { "project context: about to init key" }
                val isInitSuccess = initEncryption()
                if (isInitSuccess) {
                    logger.info { "project context index starting" }
                    delay(300)
                    val isIndexSuccess = index()
                    if (isIndexSuccess) isIndexComplete.set(true)
                    return
                }
            } catch (e: Exception) {
                if (e.stackTraceToString().contains("Connection refused")) {
                    retryCount.incrementAndGet()
                    delay(10000)
                } else {
                    return
                }
            }
        }
    }

    private suspend fun initEncryption(): Boolean {
        val request = encoderServer.getEncryptionRequest()
        val response = sendMsgToLsp(LspMessage.Initialize, request)
        return response?.responseCode == 200
    }

    suspend fun index(): Boolean {
        val projectRoot = project.basePath ?: return false

        val indexStartTime = System.currentTimeMillis()
        val filesResult = collectFiles()
        if (filesResult.files.isEmpty()) {
            logger.warn { "No file found in workspace" }
            return false
        }
        var duration = (System.currentTimeMillis() - indexStartTime).toDouble()
        logger.debug { "time elapsed to collect project context files: ${duration}ms, collected ${filesResult.files.size} files" }

        val indexOption = if (CodeWhispererSettings.getInstance().isProjectContextEnabled()) IndexOption.ALL else IndexOption.DEFAULT
        val encrypted = encryptRequest(IndexRequest(filesResult.files, projectRoot, indexOption.command, ""))
        val response = sendMsgToLsp(LspMessage.Index, encrypted)

        duration = (System.currentTimeMillis() - indexStartTime).toDouble()
        logger.debug { "project context index time: ${duration}ms" }

        val startUrl = getStartUrl(project)
        if (response?.responseCode == 200) {
            val usage = getUsage()
            recordIndexWorkspace(duration, filesResult.files.size, filesResult.fileSize, true, usage?.memoryUsage, usage?.cpuUsage, startUrl)
            logger.debug { "project context index finished for ${project.name}" }
            return true
        } else {
            logger.debug { "project context index failed" }
            recordIndexWorkspace(duration, filesResult.files.size, filesResult.fileSize, false, null, null, startUrl)
            return false
        }
    }

    // TODO: rename queryChat
    suspend fun query(prompt: String, timeout: Long?): List<RelevantDocument> = withTimeout(timeout ?: CHAT_EXPLICIT_PROJECT_CONTEXT_TIMEOUT) {
        val encrypted = encryptRequest(QueryChatRequest(prompt))
        val response = sendMsgToLsp(LspMessage.QueryChat, encrypted) ?: return@withTimeout emptyList()
        val parsedResponse = mapper.readValue<List<Chunk>>(response.responseBody)

        return@withTimeout queryResultToRelevantDocuments(parsedResponse)
    }

    suspend fun queryInline(query: String, filePath: String, target: InlineContextTarget): List<InlineBm25Chunk> = withTimeout(SUPPLEMENTAL_CONTEXT_TIMEOUT) {
        val encrypted = encryptRequest(QueryInlineCompletionRequest(query, filePath, target.toString()))
        val r = sendMsgToLsp(LspMessage.QueryInlineCompletion, encrypted) ?: return@withTimeout emptyList()
        return@withTimeout mapper.readValue<List<InlineBm25Chunk>>(r.responseBody)
    }

    suspend fun getUsage(): Usage? {
        val response = sendMsgToLsp(LspMessage.GetUsageMetrics, request = null) ?: return null
        return try {
            val parsedResponse = mapper.readValue<Usage>(response.responseBody)
            parsedResponse
        } catch (e: Exception) {
            logger.warn { "error parsing query response ${e.message}" }
            null
        }
    }

    @RequiresBackgroundThread
    fun updateIndex(filePaths: List<String>, mode: IndexUpdateMode) {
        val encrypted = encryptRequest(UpdateIndexRequest(filePaths, mode.command))
        runBlocking { sendMsgToLsp(LspMessage.UpdateIndex, encrypted) }
    }

    private fun recordIndexWorkspace(
        duration: Double,
        fileCount: Int = 0,
        fileSize: Int = 0,
        isSuccess: Boolean,
        memoryUsage: Int? = 0,
        cpuUsage: Int? = 0,
        startUrl: String? = null,
    ) {
        AmazonqTelemetry.indexWorkspace(
            project = null,
            duration = duration,
            amazonqIndexFileCount = fileCount.toLong(),
            amazonqIndexFileSizeInMB = fileSize.toLong(),
            success = isSuccess,
            amazonqIndexMemoryUsageInMB = memoryUsage?.toLong(),
            amazonqIndexCpuUsagePercentage = cpuUsage?.toLong(),
            credentialStartUrl = startUrl
        )
    }

    private fun setConnectionTimeout(connection: HttpURLConnection, timeout: Int) {
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
    }

    private fun setConnectionProperties(connection: HttpURLConnection) {
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.setRequestProperty("Accept", "text/plain")
    }

    private fun setConnectionRequest(connection: HttpURLConnection, payload: String) {
        connection.doOutput = true
        connection.outputStream.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(payload)
            }
        }
    }

    private fun willExceedPayloadLimit(currentTotalFileSize: Long, currentFileSize: Long): Boolean {
        val maxSize = CodeWhispererSettings.getInstance().getProjectContextIndexMaxSize()
        return currentTotalFileSize.let { totalSize -> totalSize > (maxSize * 1024 * 1024 - currentFileSize) }
    }

    private fun isBuildOrBin(fileName: String): Boolean {
        val regex = Regex("""bin|build|node_modules|venv|\.venv|env|\.idea|\.conda""", RegexOption.IGNORE_CASE)
        return regex.find(fileName) != null
    }

    fun collectFiles(): FileCollectionResult {
        val collectedFiles = mutableListOf<String>()
        var currentTotalFileSize = 0L
        val allFiles = mutableListOf<VirtualFile>()

        val projectBaseDirectories = project.getBaseDirectories()
        val changeListManager = ChangeListManager.getInstance(project)

        projectBaseDirectories.forEach {
            VfsUtilCore.visitChildrenRecursively(
                it,
                object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
                    // TODO: refactor this along with /dev & codescan file traversing logic
                    override fun visitFile(file: VirtualFile): Boolean {
                        if ((file.isDirectory && isBuildOrBin(file.name)) ||
                            !isWorkspaceSourceContent(file, projectBaseDirectories, changeListManager, additionalGlobalIgnoreRulesForStrictSources) ||
                            (file.isFile && file.length > 10 * 1024 * 1024)
                        ) {
                            return false
                        }
                        if (file.isFile) {
                            allFiles.add(file)
                            return false
                        }
                        return true
                    }
                }
            )
        }

        for (file in allFiles) {
            if (willExceedPayloadLimit(currentTotalFileSize, file.length)) {
                break
            }
            collectedFiles.add(file.path)
            currentTotalFileSize += file.length
        }

        return FileCollectionResult(
            files = collectedFiles.toList(),
            fileSize = (currentTotalFileSize / 1024 / 1024).toInt()
        )
    }

    private fun queryResultToRelevantDocuments(queryResult: List<Chunk>): List<RelevantDocument> {
        val documents: MutableList<RelevantDocument> = mutableListOf()
        queryResult.forEach { chunk ->
            run {
                val path = chunk.relativePath.orEmpty()
                val text = chunk.context ?: chunk.content.orEmpty()
                val document = RelevantDocument(path, text.take(10240))
                documents.add(document)
                logger.info { "project context: query retrieved document $path with content: ${text.take(200)}" }
            }
        }
        return documents
    }

    private fun encryptRequest(r: LspRequest): String {
        val payloadJson = mapper.writeValueAsString(r)
        return encoderServer.encrypt(payloadJson)
    }

    private suspend fun sendMsgToLsp(msgType: LspMessage, request: String?): LspResponse? {
        logger.info { "sending message: ${msgType.endpoint} to lsp on port ${encoderServer.port}" }
        val url = URI("http://127.0.0.1:${encoderServer.port}/${msgType.endpoint}").toURL()
        if (!encoderServer.isNodeProcessRunning()) {
            logger.warn { "language server for ${project.name} is not running" }
            return null
        }
        // use 1h as timeout for index, 5 seconds for other APIs
        val timeoutMs = if (msgType is LspMessage.Index) 60.minutes.inWholeMilliseconds.toInt() else 5000
        // dedicate single thread to index operation because it can be long running
        val dispatcher = if (msgType is LspMessage.Index) ioDispatcher(1) else ioDispatcher

        return withContext(dispatcher) {
            with(url.openConnection() as HttpURLConnection) {
                setConnectionProperties(this)
                setConnectionTimeout(this, timeoutMs)
                request?.let { r ->
                    setConnectionRequest(this, r)
                }
                val responseCode = this.responseCode
                logger.info { "receiving response for $msgType with responseCode $responseCode" }

                val responseBody = if (responseCode == 200) {
                    this.inputStream.bufferedReader().use { reader -> reader.readText() }
                } else {
                    ""
                }

                LspResponse(responseCode, responseBody)
            }
        }
    }

    override fun dispose() {
        retryCount.set(0)
    }

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
    }
}
