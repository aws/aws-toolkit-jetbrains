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
import kotlinx.coroutines.delay
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.cancellingProgressField
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller.CodeTestChatHelper
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.BuildAndExecuteStatusIcon
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteProgressStatus
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteTaskContext
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildStatus
import java.io.File
import java.io.FileWriter

fun constructBuildAndExecutionSummaryText(currentStatus: BuildAndExecuteProgressStatus, codeTestChatHelper: CodeTestChatHelper): String {
    val progressMessages = mutableListOf<String>()

    if (currentStatus >= BuildAndExecuteProgressStatus.RUN_BUILD) {
        val buildStatus = when (currentStatus) {
            BuildAndExecuteProgressStatus.RUN_BUILD -> "in progress"
            BuildAndExecuteProgressStatus.BUILD_FAILED -> "failed"
            else -> "complete"
        }
        val icon = if (buildStatus == "in progress") BuildAndExecuteStatusIcon.WAIT.icon else if (codeTestChatHelper.getActiveSession().buildStatus == BuildStatus.SUCCESS) BuildAndExecuteStatusIcon.DONE.icon else BuildAndExecuteStatusIcon.FAILED.icon
        progressMessages.add(
            "$icon ${
                if (buildStatus == "in progress") "Project compiling" else if (codeTestChatHelper.getActiveSession().buildStatus == BuildStatus.SUCCESS) "Project compiled" else "Unable to compile project"
            }"
        )
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) {
        val buildStatus = if (currentStatus == BuildAndExecuteProgressStatus.RUN_BUILD) "Running tests" else if (codeTestChatHelper.getActiveSession().buildStatus == BuildStatus.SUCCESS) "Tests passed" else "Tests failed"
        val icon = if (buildStatus == "Running tests") BuildAndExecuteStatusIcon.WAIT.icon else if (codeTestChatHelper.getActiveSession().buildStatus == BuildStatus.SUCCESS) BuildAndExecuteStatusIcon.DONE.icon else BuildAndExecuteStatusIcon.FAILED.icon
        progressMessages.add("$icon $buildStatus")
    }

    if ((currentStatus >= BuildAndExecuteProgressStatus.BUILD_FAILED) && codeTestChatHelper.getActiveSession().buildStatus == BuildStatus.FAILURE) {
        val buildStatus = if (currentStatus == BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) "Fixing" else "Fixed"
        val icon = if (currentStatus == BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS) BuildAndExecuteStatusIcon.WAIT.icon else BuildAndExecuteStatusIcon.DONE.icon
        progressMessages.add("$icon $buildStatus test failures")
    }

    if (currentStatus >= BuildAndExecuteProgressStatus.FIXING_TEST_CASES && codeTestChatHelper.getActiveSession().buildStatus == BuildStatus.FAILURE) {
        progressMessages.add("\n")
        progressMessages.add("**Results**")
        progressMessages.add("\n")
        progressMessages.add("Amazon Q executed the tests and identified at least one failure. Below are the suggested fixes.")
    }

    // Join all progress messages into a single string
    return """
            Sure, This may take a few minutes and I'll update the progress here.
        
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
    codeTestChatHelper: CodeTestChatHelper
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
    var foundGradleRoot = false
    while (packageRoot != null && packageRoot != projectRoot) {
        if (File(packageRoot, "settings.gradle.kts").exists() || File(packageRoot, "build.gradle.kts").exists() ||
            File(packageRoot, "settings.gradle").exists() || File(packageRoot, "build.gradle").exists()
        ) {
            foundGradleRoot = true
            break // Store the last valid Gradle root found
        }
        packageRoot = packageRoot.parentFile
    }
    var workingDir = if (foundGradleRoot) {
        packageRoot ?: testFileAbsolutePath.parentFile
    } else {
        testFileAbsolutePath.parentFile
    }
    // If no valid Gradle directory is found, fallback to the project root
//    val gradleWrapper = File(packageRoot ?: projectRoot, "gradlew")
    val console: ConsoleView = ConsoleViewImpl(project, true)

    // Attach Console View to Build Tool Window
    ApplicationManager.getApplication().invokeLater {
        val tabName = if (isBuildCommand) "Q TestGen Build Output" else "Q Test Gen Test Execution Output"

        // Get the BuildContentManager instance
        val buildContentManager = BuildContentManager.getInstance(project)

        val toolWindow = buildContentManager.getOrCreateToolWindow()
        val contentManager = toolWindow.contentManager

        // Check if tab already exists
        val existingContent = contentManager.contents.find { it.displayName == tabName }

        if (existingContent != null) {
            // If tab exists, remove it
            buildContentManager.removeContent(existingContent)
        }
        // Create and add new content
        val content = ContentImpl(console.component, tabName, true)
        buildContentManager.addContent(content)
        buildContentManager.setSelectedContent(content, false, false, true, null)
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
                    codeTestChatHelper.getActiveSession().buildStatus = BuildStatus.SUCCESS
                    // green color
                    console.print("\nBUILD SUCCESSFUL\n", ConsoleViewContentType.USER_INPUT)
                } else {
                    codeTestChatHelper.getActiveSession().buildStatus = BuildStatus.FAILURE
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
