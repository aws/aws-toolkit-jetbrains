// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.utils

import com.intellij.build.BuildContentManager
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.content.impl.ContentImpl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.getBuildIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.getExecutionIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.getFixingTestCasesIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteProgressStatus
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteTaskContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

fun constructBuildAndExecutionSummaryText(currentStatus: BuildAndExecuteProgressStatus, iterationNum: Int): String {
    val progressMessages = mutableListOf<String>()

    if (currentStatus >= BuildAndExecuteProgressStatus.RUN_BUILD) {
        val verb = if (currentStatus == BuildAndExecuteProgressStatus.RUN_BUILD) "in progress" else "complete"
        progressMessages.add("${getBuildIcon(currentStatus)}: Build $verb")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) {
        val verb = if (currentStatus == BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) "Executing" else "Executed"
        progressMessages.add("${getExecutionIcon(currentStatus)}: $verb passed tests")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.FIXING_TEST_CASES) {
        val verb = if (currentStatus == BuildAndExecuteProgressStatus.FIXING_TEST_CASES) "Fixing" else "Fixed"
        progressMessages.add("${getFixingTestCasesIcon(currentStatus)}: $verb errors in tests")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.PROCESS_TEST_RESULTS) {
        progressMessages.add("\n")
        progressMessages.add("**Test case summary**")
        progressMessages.add("\n")
        progressMessages.add("Unit test coverage X%")
        progressMessages.add("Build fails Y")
        progressMessages.add("Assertion fails Z")
    }

    val prefix =
        if (iterationNum < 2) {
            "Sure"
        } else {
            val timeString = when (iterationNum) {
                2 -> "second"
                3 -> "third"
                4 -> "fourth"
                // shouldn't reach
                else -> "fifth"
            }
            "Working on the $timeString iteration now"
        }

    // Join all progress messages into a single string
    return """
            $prefix. This may take a few minutes and I'll update the progress here.
        
            **Progress summary**
            
    """.trimIndent() + progressMessages.joinToString("\n")
}

fun runBuildOrTestCommand(
    localCommand: String,
    tmpFile: VirtualFile,
    project: Project,
    isBuildCommand: Boolean,
    buildAndExecuteTaskContext: BuildAndExecuteTaskContext,
) {
    if (localCommand.isEmpty()) {
        buildAndExecuteTaskContext.testExitCode = 0
        return
    }
    val repositoryPath = project.basePath ?: return
    val commandParts = localCommand.split(" ")
    val command = commandParts.first()
    val args = commandParts.drop(1)
    val file = File(tmpFile.path)

    // Create Console View for Build Output
    val console: ConsoleView = ConsoleViewImpl(project, true)

    // Attach Console View to Build Tool Window
    ApplicationManager.getApplication().invokeLater {
        val tabName = if (isBuildCommand) "Q TestGen Build Output" else "Q Test Gen Test Execution Output"
        val content = ContentImpl(console.component, tabName, true)
        BuildContentManager.getInstance(project).addContent(content)
        // TODO: remove these tabs when they are not needed
        BuildContentManager.getInstance(project).setSelectedContent(content, false, false, true, null)
    }

    val processBuilder = ProcessBuilder()
        .command(listOf(command) + args)
        .directory(File(repositoryPath))
        .redirectErrorStream(true)

    try {
        val process = processBuilder.start()
        val processHandler: ProcessHandler = OSProcessHandler(process, localCommand, null)

        // Attach Process Listener for Output Handling
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val cleanedText = cleanText(event.text)
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        file.appendText(cleanedText)
                    }
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                val exitCode = event.exitCode
                if (exitCode == 0) {
                    // green color
                    console.print("\nBUILD SUCCESSFUL\n", ConsoleViewContentType.USER_INPUT)
                } else {
                    // red color
                    console.print("\nBUILD FAILED with exit code $exitCode\n", ConsoleViewContentType.ERROR_OUTPUT)
                }
                if (isBuildCommand) {
                    buildAndExecuteTaskContext.buildExitCode = exitCode
                } else {
                    buildAndExecuteTaskContext.testExitCode = exitCode
                }
            }
        })

        // Start Process and Notify
        console.attachToProcess(processHandler)
        processHandler.startNotify()
        console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
    } catch (e: Exception) {
        console.print("Error executing command: $localCommand\n", ConsoleViewContentType.ERROR_OUTPUT)
        console.print("$e", ConsoleViewContentType.ERROR_OUTPUT)
        if (isBuildCommand) {
            buildAndExecuteTaskContext.buildExitCode = 1
        } else {
            buildAndExecuteTaskContext.testExitCode = 1
        }
        return
    }
}

private fun cleanText(input: String): String {
    val cleaned = StringBuilder()
    for (char in input) {
        if (char == '\b' && cleaned.isNotEmpty()) {
            // Remove the last character when encountering a backspace
            cleaned.deleteCharAt(cleaned.length - 1)
        } else if (char != '\b') {
            cleaned.append(char)
        }
    }
    return cleaned.toString()
}

suspend fun combineBuildAndExecuteLogFiles(
    buildLogFile: VirtualFile?,
    testLogFile: VirtualFile?,
): VirtualFile? {
    if (buildLogFile == null || testLogFile == null) return null
    val buildLogFileContent = String(buildLogFile.contentsToByteArray(), StandardCharsets.UTF_8)
    val testLogFileContent = String(testLogFile.contentsToByteArray(), StandardCharsets.UTF_8)

    val combinedContent = "Build Output:\n$buildLogFileContent\nTest Execution Output:\n$testLogFileContent"

    // Create a new virtual file and write combined content
    val newFile = VirtualFileManager.getInstance().findFileByNioPath(
        withContext(currentCoroutineContext()) {
            Files.createTempFile(null, null)
        }
    )
    withContext(EDT) {
        ApplicationManager.getApplication().runWriteAction {
            newFile?.setBinaryContent(combinedContent.toByteArray(StandardCharsets.UTF_8))
        }
    }

    return newFile
}
