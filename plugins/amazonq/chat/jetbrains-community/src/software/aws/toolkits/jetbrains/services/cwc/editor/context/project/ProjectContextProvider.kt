// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPlainText
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Stack

class ProjectContextProvider (val project: Project, private val encoderServer: EncoderServer) : Disposable{
    private val scope = disposableCoroutineScope(this)
    init {
        scope.launch {
            if (CodeWhispererSettings.getInstance().isProjectContextEnabled()) {
                while (true) {
                    if (encoderServer.isNodeProcessRunning()) {
                        // TODO: need better solution for this
                        delay(15000)
                        initAndIndex()
                        break
                    } else {
                        delay(300)
                    }
                }
            }
        }
    }
    data class IndexRequestPayload(
        val filePaths: List<String>,
        val projectRoot: String,
        val refresh: Boolean
    )

    data class QueryRequestPayload (
        val query: String
    )

    data class UpdateIndexRequestPayload (
        val filePath: String
    )

   data class Chunk (
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("filePath")
       val filePath: String ?= null,
        @JsonProperty("content")
       val content: String?= null,
        @JsonProperty("id")
       val id: String?= null,
        @JsonProperty("index")
       val index: String?= null,
        @JsonProperty("vec")
       val vec: List<String>?= null,
        @JsonProperty("context")
       val context: String?= null,
        @JsonProperty("prev")
       val prev: String?= null,
        @JsonProperty("next")
       val next: String?= null,
        @JsonProperty("relativePath")
       val relativePath: String?= null,
        @JsonProperty("programmingLanguage?")
        val programmingLanguage: String?= null,
   )

    fun initAndIndex () {
        initEncryption()
        index()
    }

    private fun initEncryption() : Boolean {
        val url = URL("http://localhost:${encoderServer.currentPort}/initialize")
        val payload = encoderServer.getEncryptionRequest()
        return with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "text/plain")
            setRequestProperty("Accept", "text/plain")
            doOutput = true
            OutputStreamWriter(outputStream).use {
                it.write(payload)
            }
            logger.info("project context initialize response code: $responseCode")
            if (responseCode == 200) return true else false
        }
    }

    fun index() : Boolean {
        val url = URL("http://localhost:${encoderServer.currentPort}/indexFiles")
        val files = collectFiles().toList()
        val projectRoot = project.guessProjectDir()?.path ?: return false
        val payload = IndexRequestPayload(files, projectRoot, true)
        val payloadJson = Gson().toJson(payload)
        val encrypted = encoderServer.encrypt(payloadJson)
        return with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "text/plain")
            setRequestProperty("Accept", "text/plain")
            doOutput = true
            OutputStreamWriter(outputStream).use {
                it.write(encrypted)
            }
            logger.info("project context index response code: $responseCode")
            if (responseCode == 200) return true else false
        }
    }

    fun query(prompt: String): List<RelevantDocument> {
        val url = URL("http://localhost:${encoderServer.currentPort}/query")
        val payload = QueryRequestPayload(prompt)
        val payloadJson = Gson().toJson(payload)
        val encrypted = encoderServer.encrypt(payloadJson)

        val connection = url.openConnection() as HttpURLConnection

        // Set the connect and read timeouts
        connection.connectTimeout = 5000 // 5 seconds
        connection.readTimeout = 10000 // 10 second

        // Set any other request properties or parameter
        // Set the request method to POST
        connection.requestMethod = "POST"

        // Set the request headers
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.setRequestProperty("Accept", "text/plain")

        // Enable output for the request body
        connection.doOutput = true
        connection.outputStream.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(encrypted)
            }
        }
        // Connect to the server
        val responseCode = connection.responseCode
        logger.info("project context query response code: $responseCode")
        val responseBody = if (responseCode == 200) {
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } else {
            ""
        }

        val mapper = ObjectMapper()
        try {
            val parsedResponse = mapper.readValue<List<Chunk>>(responseBody)
            return queryResultToRelevantDocuments(parsedResponse)
        } catch (e: Exception) {
            logger.warn("error parsing query response ${e.message}")
            return emptyList()
        }
        connection.disconnect()
    }

    fun updateIndex(filePath: String) {
        val url = URL("http://localhost:${encoderServer.currentPort}/updateIndex")
        val payload = UpdateIndexRequestPayload(filePath)
        val payloadJson = Gson().toJson(payload)
        val encrypted = encoderServer.encrypt(payloadJson)
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "text/plain")
            setRequestProperty("Accept", "text/plain")
            doOutput = true
            OutputStreamWriter(outputStream).use {
                it.write(encrypted)
            }
            val responseCode = responseCode
            logger.debug("project context update index response code: $responseCode")
            return
        }
    }

    private fun willExceedPayloadLimit(currentTotalFileSize: Long, currentFileSize: Long): Boolean =
        currentTotalFileSize.let { totalSize -> totalSize > (200 * 1024 * 1024 - currentFileSize) }

    private fun isBuildOrBin (filePath: String): Boolean {
        val regex = Regex("""[/\\](bin|build|node_modules)[/\\]""", RegexOption.IGNORE_CASE)
        return regex.find(filePath) != null
    }

    private fun collectFiles(): MutableSet<String> {
        val files = mutableSetOf<String>()
        val traversedDirectories = mutableSetOf<VirtualFile>()
        var currentTotalFileSize = 0L
        val stack = Stack<VirtualFile>()
        moduleLoop@ for (module in project.modules) {
            if (module.guessModuleDir() != null) {
                stack.push(module.guessModuleDir())
                while (stack.isNotEmpty()) {
                    val current = stack.pop()

                    if (!current.isDirectory) {
                        if (current.isFile) {
                            if(isBuildOrBin(current.path) || current.length > 10 * 1024 * 102){
                                continue
                            }
                            if (willExceedPayloadLimit(currentTotalFileSize, current.length)) {
                                break
                            } else {
                                try {
                                    val language = current.programmingLanguage()
                                    if (language != CodeWhispererPlainText.INSTANCE && language != CodeWhispererUnknownLanguage.INSTANCE) {
                                        files.add(current.path)
                                        currentTotalFileSize += current.length
                                    }
                                } catch (e: Exception) {
                                    logger.debug { "Error parsing the file: ${current.path} with error: ${e.message}" }
                                    continue
                                }
                            }
                        }
                    } else {
                        if (
                            !traversedDirectories.contains(current) && current.isValid
                        ) {
                            for (child in current.children) {
                                stack.push(child)
                            }
                        }
                        traversedDirectories.add(current)
                    }
                }
            }
        }
        return files
    }

    private fun queryResultToRelevantDocuments(queryResult: List<Chunk>) : List<RelevantDocument> {
        val chunksMap: MutableMap<String, MutableList<Chunk>> = mutableMapOf()
        queryResult.forEach { chunk ->
            run {
                if(chunk.relativePath == null) return@forEach
                val list: MutableList<Chunk> = if (chunksMap.containsKey(chunk.relativePath)) chunksMap[chunk.relativePath]!! else mutableListOf()
                list.add(chunk)
                chunksMap[chunk.relativePath] = list
            }
        }
        val documents: MutableList<RelevantDocument> = mutableListOf()
        chunksMap.forEach{filePath, chunkList ->
        run {
            var text = ""
            chunkList.forEach() { chunk -> text += (chunk.context ?: chunk.content)}
            val document = RelevantDocument(filePath, text)
            documents.add(document)
        }}
        return documents
    }

    override fun dispose() {
       // clear index?
    }

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
        fun getInstance(project: Project, encoderServer: EncoderServer) = ProjectContextProvider(project, encoderServer)
    }

}
