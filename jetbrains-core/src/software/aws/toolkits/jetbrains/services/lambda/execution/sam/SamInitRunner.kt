// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.sam

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.settings.SamSettings

internal class SamInitRunner {
    private val samCliExecutable = SamSettings.getInstance().executablePath
    private var commandLine = GeneralCommandLine(samCliExecutable)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withParameters("init")

    fun applyRuntime(runtime: Runtime) = applyParameter("--runtime", runtime.toString())

    fun applyName(name: String) = applyParameter("--name", name)

    fun applyOutputDir(location: VirtualFile) = applyParameter("--output-dir", location.path)

    private fun applyParameter(flag: String, value: String) = apply {
        commandLine = commandLine.withParameters(flag)
                .withParameters(value)
    }

    fun execute() {
        val process = CapturingProcessHandler(commandLine).runProcess()
        if (process.exitCode != 0) {
            throw RuntimeException("Could not execute `sam init`!: ${process.stderrLines.last()}")
        }
    }
}