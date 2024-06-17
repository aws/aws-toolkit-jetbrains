// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPlainText
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Stack

@Service(Service.Level.PROJECT)
class ProjectContextProvider (val project: Project) : Disposable{
    private var encoderServer: EncoderServer = EncoderServer.getInstance(project)

    init {
        encoderServer.start()
        projectCoroutineScope(project).launch{
            delay(3000)
            index()
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
   )

    private fun index() {
        if (!encoderServer.isServerRunning()) {
            logger.info("encoder server is not running, skipping index")
            return
        }
        val port = encoderServer.currentPort
        val url = URL("http://localhost:$port/indexFiles")
        val files = collectFiles().toList()
        val projectRoot = project.guessProjectDir()?.path ?: return
        val payload = IndexRequestPayload(files, projectRoot, true)
        val payloadJson = Gson().toJson(payload)
        try{
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                OutputStreamWriter(outputStream).use {
                    it.write(payloadJson)
                }
                val responseCode = responseCode
                logger.info("Index Response: $responseCode")
            }
        } catch (e: Exception){
            logger.info("error while indexing: ${e.message}")
        }
    }

    fun query(prompt: String): List<RelevantDocument> {
        if (!encoderServer.isServerRunning()) {
            logger.info("encoder server is not running, skipping query")
            return emptyList()
        }
        val port = encoderServer.currentPort
        val url = URL("http://localhost:$port/query")
        val payload = QueryRequestPayload(prompt)
        val payloadJson = Gson().toJson(payload)
        try {
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                OutputStreamWriter(outputStream).use {
                    it.write(payloadJson)
                }

                val responseBody = if (responseCode == 200) {
                    inputStream.bufferedReader().use { it.readText() }
                } else {
                    ""
                }

                val mapper = ObjectMapper()
                try {
                    val parsedResponse = mapper.readValue<List<Chunk>>(responseBody)
                    return queryResultToRelevantDocuments(parsedResponse)
                } catch (e: Exception) {
                    logger.info("error parsing query response ${e.message}")
                    return emptyList()
                }
            }
        } catch (e: Exception) {
            logger.info("error while querying: ${e.message}")
            return emptyList()
        }
    }

    fun updateIndex(filePath: String) {
        val port = encoderServer.currentPort
        val url = URL("http://localhost:$port/updateIndex")
        val payload = UpdateIndexRequestPayload(filePath)
        val payloadJson = Gson().toJson(payload)
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            OutputStreamWriter(outputStream).use {
                it.write(payloadJson)
            }
            val responseCode = responseCode
            logger.info("update index response: $responseCode")
        }
    }

    private fun willExceedPayloadLimit(currentTotalFileSize: Long, currentFileSize: Long): Boolean =
        currentTotalFileSize.let { totalSize -> totalSize > (400 * 1024 * 1024 - currentFileSize) }

    private fun isBuildOrBin (filePath: String): Boolean {
        val regex = Regex("""[/\\](bin|build|node_modules)[/\\]""", RegexOption.IGNORE_CASE)
        return regex.find(filePath) != null
    }

    private fun collectFiles(): MutableSet<String> {
        val files = mutableSetOf<String>()
        val traversedDirectories = mutableSetOf<VirtualFile>()
        var currentTotalFileSize = 0L
        val stack = Stack<VirtualFile>()
        logger.info("modules: ${project.modules}")
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
                if(chunk.filePath == null) return@forEach
                val list: MutableList<Chunk> = if (chunksMap.containsKey(chunk.filePath)) chunksMap[chunk.filePath]!! else mutableListOf()
                list.add(chunk)
                chunksMap[chunk.filePath] = list
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
        encoderServer.dispose()
    }

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
        fun getInstance(project: Project) = project.service<ProjectContextProvider>()
    }

}
