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
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.CHAT_EXPLICIT_PROJECT_CONTEXT_TIMEOUT
import software.aws.toolkits.jetbrains.services.amazonq.FeatureDevSessionContext
import software.aws.toolkits.jetbrains.services.amazonq.SUPPLEMENTAL_CONTEXT_TIMEOUT
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.AmazonqTelemetry
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue

class ProjectContextProvider(val project: Project, private val encoderServer: EncoderServer, private val cs: CoroutineScope) : Disposable {
    private val retryCount = AtomicInteger(0)
    val isIndexComplete = AtomicBoolean(false)
    private val mapper = jacksonObjectMapper()

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

    private fun initAndIndex() {
        cs.launch {
            while (retryCount.get() < 5) {
                try {
                    logger.info { "project context: waiting for server to start" }
                    val isInitSuccess = encoderServer.encoderServer2.initializer.isCompleted
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

    fun index(): Boolean {
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
        val startUrl = getStartUrl(project)
        try {
            val (_, indexTime) = measureTimedValue {
                encoderServer.encoderServer2.languageServer.buildIndex(IndexRequest(filesResult.files, projectRoot, indexOption.command, ""))
            }

            logger.debug { "project context index time: ${indexTime}ms" }
        } catch (e: Exception) {
            logger.debug { "project context index failed" }
            recordIndexWorkspace(duration, filesResult.files.size, filesResult.fileSize, false, null, null, startUrl)
            return false
        }

        val usage = getUsage()
        recordIndexWorkspace(duration, filesResult.files.size, filesResult.fileSize, true, usage?.memoryUsage, usage?.cpuUsage, startUrl)
        logger.debug { "project context index finished for ${project.name}" }
        return true
    }

    // TODO: rename queryChat
    suspend fun query(prompt: String, timeout: Long?): List<RelevantDocument> = withTimeout(timeout ?: CHAT_EXPLICIT_PROJECT_CONTEXT_TIMEOUT) {
        val response = try {
            encoderServer.encoderServer2.languageServer.queryChat(QueryChatRequest(prompt)).await()
        } catch (e: Exception) {
            logger.warn { "error querying chat ${e.message}" }
            return@withTimeout emptyList()
        }
        queryResultToRelevantDocuments(response)
    }

    suspend fun queryInline(query: String, filePath: String, target: InlineContextTarget): List<InlineBm25Chunk> = withTimeout(SUPPLEMENTAL_CONTEXT_TIMEOUT) {
        try {
            encoderServer.encoderServer2.languageServer.queryInline(QueryInlineCompletionRequest(query, filePath, target.toString())).await()
        } catch (e: Exception) {
            logger.warn { "error querying chat ${e.message}" }
            return@withTimeout emptyList()
        }
    }

    fun getUsage(): Usage? = runBlocking {
        try {
            encoderServer.encoderServer2.languageServer.getUsageMetrics().await()
        } catch (e: Exception) {
            logger.warn { "error parsing query response ${e.message}" }
            null
        }
    }

    fun updateIndex(filePaths: List<String>, mode: IndexUpdateMode) {
        encoderServer.encoderServer2.languageServer.updateIndex(UpdateIndexRequest(filePaths, mode.command))
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

    override fun dispose() {
        retryCount.set(0)
    }

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
    }
}
