// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.SemVer
import software.aws.toolkits.jetbrains.core.executables.AutoResolvable
import software.aws.toolkits.jetbrains.core.executables.ExecutableCommon
import software.aws.toolkits.jetbrains.core.executables.ExecutableType
import software.aws.toolkits.jetbrains.core.executables.Validatable
import software.aws.toolkits.jetbrains.settings.ExecutableDetector
import java.nio.file.Path
import java.nio.file.Paths

class AwsCliExecutable : ExecutableType<SemVer>, AutoResolvable, Validatable {
    override val displayName: String = "aws"
    override val id: String = "awsCli"
    override fun version(path: Path): SemVer =
        ExecutableCommon.getVersion(path.toString(), AwsCliVersionCache, this.displayName)

    override fun validate(path: Path) {
        val version = this.version(path)
        ExecutableCommon.checkSemVerVersion(version, MIN_VERSION, MAX_VERSION, this.displayName)
    }

    override fun resolve(): Path? {
        val path = (
            if (SystemInfo.isWindows) {
                ExecutableDetector().find(
                    arrayOf("C:\\Program Files\\Amazon\\AWSCLI\\bin", "C:\\Program Files (x86)\\Amazon\\AWSCLI\\bin"),
                    arrayOf("aws.cmd", "aws.exe")
                )
            } else {
                ExecutableDetector().find(
                    arrayOf("/usr/local/bin", "/usr/bin"),
                    arrayOf("aws")
                )
            }
            ) ?: return null
        return Paths.get(path)
    }

    companion object {
        val MAX_VERSION: SemVer = SemVer("3.0.0", 3, 0, 0) // exclusive

        val MIN_VERSION: SemVer = SemVer("1.0.0", 1, 0, 0) // inclusive
    }
}

fun GeneralCommandLine.execCommand(
    environmentVariables: Map<String, String>,
    clusterArn: String, task: String,
    shell: String,
    containerName: String
) = this.apply {
    withParameters("ecs")
    withParameters("execute-command")
    withParameters("--cluster")
    withParameters(clusterArn)
    withParameters("--task")
    withParameters(task)
    withParameters("--command")
    withParameters(shell)
    withParameters("--interactive")
    withParameters("--container")
    withParameters(containerName)
    withEnvironment(environmentVariables)
}
