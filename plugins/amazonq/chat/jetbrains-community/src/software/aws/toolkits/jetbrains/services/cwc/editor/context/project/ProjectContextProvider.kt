// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.editor.context.project

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
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

class ProjectContextProvider (private val project: Project){
    init {
        projectCoroutineScope(project).launch(){
            index()
        }
    }

    data class RequestPayload(
        val filePaths: List<String>,
        val projectRoot: String,
        val refresh: Boolean
    )

    private fun index() {
        val url = URL("http://localhost:3000/indexFiles")
        val files = collectFiles().toList()
        val projectRoot = project.guessProjectDir()?.path ?: return
        val payload = RequestPayload(files, projectRoot, true)
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
            println("Response: $responseCode")
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

    companion object {
        private val logger = getLogger<ProjectContextProvider>()
    }
}
