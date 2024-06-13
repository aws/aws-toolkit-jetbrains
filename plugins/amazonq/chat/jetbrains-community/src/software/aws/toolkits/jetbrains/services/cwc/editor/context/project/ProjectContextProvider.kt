// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.google.gson.Gson

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import software.aws.toolkits.core.utils.info
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
    }

    fun query(prompt: String): String {
        if(!encoderServer.isServerRunning()) {
            logger.info("encoder server is not running, skipping query")
            return ""
        }
        val port = encoderServer.currentPort
        val url = URL("http://localhost:$port/query")
        val payload = QueryRequestPayload(prompt)
        val payloadJson = Gson().toJson(payload)
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            OutputStreamWriter(outputStream).use {
                it.write(payloadJson)
            }

            val responseBody = if (responseCode == 200) { inputStream.bufferedReader().use { it.readText() }} else {
                ""
            }
            return responseBody
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
        logger.info {"number of files indexed: $files.length"}
        return files
    }

    override fun dispose() {
        encoderServer.dispose()
    }

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
        fun getInstance(project: Project) = project.service<ProjectContextProvider>()
    }
}
