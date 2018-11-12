// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.sam

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.settings.SamSettings
import software.aws.toolkits.resources.message

class SamInitRunner {
    private val samCliExecutable = SamSettings.getInstance().executablePath
    private val commandLine = GeneralCommandLine(samCliExecutable)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withParameters("init")
            .withParameters("--no-input")
    private lateinit var outputDir: VirtualFile
    private val parameters = mutableMapOf<String, String>()

    fun applyLocation(location: String) = applyParameter("--location", location)

    fun applyName(name: String) = applyParameter("--name", name)

    fun applyOutputDir(location: VirtualFile) = apply {
        outputDir = location
    }

    fun applyRuntime(runtime: Runtime) = applyParameter("--runtime", runtime.toString())

    private fun applyParameter(flag: String, value: String) = apply {
        parameters[flag] = value
    }

    fun execute() = ApplicationManager.getApplication().runWriteAction {
        // set output to a temp dir
        val tempDir = LocalFileSystem.getInstance().findFileByIoFile(createTempDir())
            ?: throw RuntimeException("Cannot create temp file")
        applyParameter("--output-dir", tempDir.path)

        // run
        val commandLine = commandLine.withParameters(parameters.flatMap { listOf(it.key, it.value) })
        val process = CapturingProcessHandler(commandLine).runProcess()
        if (process.exitCode != 0) {
            throw RuntimeException("${message("sam.init.execution_error")}: ${process.stderrLines.last()}")
        }

        // copy from temp dir to output dir
        VfsUtil.copyDirectory(null, VfsUtil.getChildren(tempDir)[0], outputDir, null)
    }
}