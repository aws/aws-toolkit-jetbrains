// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.utils

import com.intellij.build.BuildContentManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.content.impl.ContentImpl
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.BuildAndExecuteStatusIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.getBuildIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.getExecutionIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.getFixingTestCasesIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteProgressStatus
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteTaskContext
import java.io.File
import java.io.FileWriter

fun constructBuildAndExecutionSummaryText(currentStatus: BuildAndExecuteProgressStatus, iterationNum: Int): String {
    val progressMessages = mutableListOf<String>()

    if (currentStatus >= BuildAndExecuteProgressStatus.RUN_BUILD) {
        val verb = when (currentStatus) {
            BuildAndExecuteProgressStatus.RUN_BUILD -> "in progress"
            BuildAndExecuteProgressStatus.BUILD_FAILED -> "failed"
            else -> "complete"
        }
        progressMessages.add("${getBuildIcon(currentStatus)}: Project compiled $verb")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) {
        val verb = if (currentStatus == BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) "Executing" else "Executed"
        progressMessages.add("${getExecutionIcon(currentStatus)}: $verb Ran tests")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.FIXING_TEST_CASES || currentStatus == BuildAndExecuteProgressStatus.BUILD_FAILED) {
        val verb = if (currentStatus == BuildAndExecuteProgressStatus.FIXING_TEST_CASES) "Fixing" else "Fixed"
        progressMessages.add("${getFixingTestCasesIcon(currentStatus)}: $verb errors in tests")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.PROCESS_TEST_RESULTS) {
        progressMessages.add("\n")
        progressMessages.add("**Test case summary**")
        progressMessages.add("\n")
        progressMessages.add(BuildAndExecuteStatusIcon.DONE.icon + "Build Success")
        progressMessages.add(BuildAndExecuteStatusIcon.DONE.icon + "Assertion Success")
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
    testFileRelativePathToProjectRoot: String,
) {
    val brazilPath = "${System.getProperty("user.home")}/.toolbox/bin:/usr/local/bin:/usr/bin:/bin:/sbin"
    if (localCommand.isEmpty()) {
        buildAndExecuteTaskContext.testExitCode = 0
        return
    }
    val projectRoot = File(project.basePath ?: return)
    val testFileAbsolutePath = File(projectRoot, testFileRelativePathToProjectRoot)
    val file = File(tmpFile.path)

    // Find the nearest Gradle root directory
    var packageRoot: File? = testFileAbsolutePath.parentFile
    while (packageRoot != null && packageRoot != projectRoot) {
        if (File(packageRoot, "settings.gradle.kts").exists() || File(packageRoot, "build.gradle.kts").exists() ||
            File(packageRoot, "settings.gradle").exists() || File(packageRoot, "build.gradle").exists()
        ) {
            break // Stop when we find a valid Gradle project root
        }
        packageRoot = packageRoot.parentFile
    }
    // If no valid Gradle directory is found, fallback to the project root
    val gradleWrapper = File(packageRoot, "gradlew")
    val workingDir = if (gradleWrapper.exists()) packageRoot else projectRoot
    val console: ConsoleView = ConsoleViewImpl(project, true)

    // Attach Console View to Build Tool Window
    ApplicationManager.getApplication().invokeLater {
        val tabName = if (isBuildCommand) "Q TestGen Build Output" else "Q Test Gen Test Execution Output"
        val content = ContentImpl(console.component, tabName, true)
        BuildContentManager.getInstance(project).addContent(content)
        BuildContentManager.getInstance(project).setSelectedContent(content, false, false, true, null)
    }

    val commandLine = when {
        System.getProperty("os.name").lowercase().contains("win") -> {
            GeneralCommandLine("cmd.exe", "/c", "set PATH=%PATH%;$brazilPath && $localCommand")
        }
        else -> {
            GeneralCommandLine("sh", "-c", "export PATH=\"$brazilPath\" && $localCommand")
        }
    }.withWorkDirectory(workingDir)

    try {
        // val process = processBuilder.start()
        val processHandler = OSProcessHandler(commandLine)

        // Attach Process Listener for Output Handling
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val cleanedText = cleanText(event.text)
                FileWriter(file, true).use { writer ->
                    writer.append(cleanedText)
                    writer.append("\n")
                }
                ApplicationManager.getApplication().invokeLater {
                    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file.toPath())?.refresh(false, false)
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
