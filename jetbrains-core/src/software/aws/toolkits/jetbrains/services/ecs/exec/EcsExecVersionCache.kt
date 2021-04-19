// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.util.text.SemVer
import com.intellij.util.text.nullize
import software.aws.toolkits.jetbrains.core.executables.ExecutableCommon
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.utils.FileInfoCache
import software.aws.toolkits.resources.message

object EcsExecVersionCache : FileInfoCache<SemVer>() {
    override fun getFileInfo(path: String): SemVer {
        val executableName = "aws"
        val sanitizedPath = path.nullize(true) ?: throw RuntimeException("Not configured")
        val commandLine = ExecutableCommon.getCommandLine(sanitizedPath, executableName).withParameters("--version")
        val process = CapturingProcessHandler(commandLine).runProcess()

        if (process.exitCode != 0) {
            val output = process.stderr.trimEnd()
            throw IllegalStateException(output)
        } else {
            val output = process.stdout.trimEnd()
            if (output.isEmpty()) {
                throw IllegalStateException(output)
            }
            val cliVersion = output.substringAfter("aws-cli/").substringBefore(" ")
            return SemVer.parseFromText(cliVersion) ?: throw IllegalStateException("no parse")
        }
    }
}
