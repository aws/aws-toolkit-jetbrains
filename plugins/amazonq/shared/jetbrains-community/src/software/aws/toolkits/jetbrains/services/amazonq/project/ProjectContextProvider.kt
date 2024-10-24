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
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.isFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.AmazonqTelemetry
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ProjectContextProvider(val project: Project, private val encoderServer: EncoderServer, private val cs: CoroutineScope) : Disposable {
    private val retryCount = AtomicInteger(0)
    val isIndexComplete = AtomicBoolean(false)
    private val mapper = jacksonObjectMapper()

    init {
        cs.launch {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                return@launch
            }

            while (true) {
                if (encoderServer.isNodeProcessRunning()) {
                    // TODO: need better solution for this
                    delay(10000)
                    initAndIndex()
                    break
                } else {
                    yield()
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

    private fun initAndIndex() {
        cs.launch {
            while (retryCount.get() < 5) {
                try {
                    logger.info { "project context: about to init key" }
                    val isInitSuccess = initEncryption()
                    if (isInitSuccess) {
                        logger.info { "project context index starting" }
                        delay(300)
                        val isIndexSuccess = index()
                        if (isIndexSuccess) isIndexComplete.set(true)
                        return@launch
                    }
                } catch (e: Exception) {
                    if (e.stackTraceToString().contains("Connection refused")) {
                        retryCount.incrementAndGet()
                        delay(10000)
                    } else {
                        return@launch
                    }
                }
            }
        }
    }

    private fun initEncryption(): Boolean {
        val request = encoderServer.getEncryptionRequest()
        val response = sendMsgToLsp(LspMessage.Initialize, request)
        return response.responseCode == 200
    }

    fun index(): Boolean {
        val projectRoot = project.basePath ?: return false

        val indexStartTime = System.currentTimeMillis()
        val filesResult = collectFiles()
        var duration = (System.currentTimeMillis() - indexStartTime).toDouble()
        logger.debug { "time elapsed to collect project context files: ${duration}ms, collected ${filesResult.files.size} files" }

        val indexOption = if (CodeWhispererSettings.getInstance().isProjectContextEnabled()) IndexOption.ALL else IndexOption.DEFAULT
        val encrypted = encryptRequest(IndexRequest(filesResult.files, projectRoot, indexOption.command, ""))
        val response = sendMsgToLsp(LspMessage.Index, encrypted)

        duration = (System.currentTimeMillis() - indexStartTime).toDouble()
        logger.debug { "project context index time: ${duration}ms" }

        val startUrl = getStartUrl(project)
        if (response.responseCode == 200) {
            val usage = getUsage()
            recordIndexWorkspace(duration, filesResult.files.size, filesResult.fileSize, true, usage?.memoryUsage, usage?.cpuUsage, startUrl)
            logger.debug { "project context index finished for ${project.name}" }
            return true
        } else {
            recordIndexWorkspace(duration, filesResult.files.size, filesResult.fileSize, false, null, null, startUrl)
            return false
        }
    }

    // TODO: rename queryChat
    fun query(prompt: String): List<RelevantDocument> {
        val encrypted = encryptRequest(QueryChatRequest(prompt))
        val response = sendMsgToLsp(LspMessage.QueryChat, encrypted)

        return try {
            val parsedResponse = mapper.readValue<List<Chunk>>(response.responseBody)
            queryResultToRelevantDocuments(parsedResponse)
        } catch (e: Exception) {
            logger.error { "error parsing query response ${e.message}" }
            throw e
        }
    }

    suspend fun queryInline(query: String, filePath: String): List<InlineBm25Chunk> = withTimeout(50L) {
        cs.async {
            val encrypted = encryptRequest(QueryInlineCompletionRequest(query, filePath))
            val r = sendMsgToLsp(LspMessage.QueryInlineCompletion, encrypted)
            return@async mapper.readValue<List<InlineBm25Chunk>>(r.responseBody)
        }.await()
    }

    fun getUsage(): Usage? {
        val response = sendMsgToLsp(LspMessage.GetUsageMetrics, request = null)
        return try {
            val parsedResponse = mapper.readValue<Usage>(response.responseBody)
            parsedResponse
        } catch (e: Exception) {
            logger.warn { "error parsing query response ${e.message}" }
            null
        }
    }

    fun updateIndex(filePaths: List<String>, mode: IndexUpdateMode) {
        val encrypted = encryptRequest(UpdateIndexRequest(filePaths, mode.command))
        sendMsgToLsp(LspMessage.UpdateIndex, encrypted)
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

    private fun setConnectionTimeout(connection: HttpURLConnection) {
        connection.connectTimeout = 5000 // 5 seconds
        connection.readTimeout = 5000 // 5 second
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

    private fun collectFiles(): FileCollectionResult {
        val collectedFiles = mutableListOf<String>()
        var currentTotalFileSize = 0L
        val featureDevSessionContext = FeatureDevSessionContext(project)
        val allFiles = mutableListOf<VirtualFile>()
        project.getBaseDirectories().forEach {
            VfsUtilCore.visitChildrenRecursively(
                it,
                object : VirtualFileVisitor<Unit>(NO_FOLLOW_SYMLINKS) {
                    // TODO: refactor this along with /dev & codescan file traversing logic
                    override fun visitFile(file: VirtualFile): Boolean {
                        if ((file.isDirectory && isBuildOrBin(file.name)) ||
                            runBlocking { featureDevSessionContext.ignoreFile(file.name) } ||
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

    private fun sendMsgToLsp(msgType: LspMessage, request: String?): LspResponse {
        logger.info { "sending message: ${msgType.endpoint} to lsp on port ${encoderServer.port}" }
        val url = URL("http://localhost:${encoderServer.port}/${msgType.endpoint}")

        return with(url.openConnection() as HttpURLConnection) {
            setConnectionProperties(this)
            setConnectionTimeout(this)
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

    override fun dispose() {
        retryCount.set(0)
    }

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
    }
}
