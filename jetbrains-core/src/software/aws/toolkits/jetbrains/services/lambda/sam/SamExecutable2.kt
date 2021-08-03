// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.SemVer
import software.aws.toolkits.jetbrains.core.executables.AutoResolvable
import software.aws.toolkits.jetbrains.core.executables.ExecutableType2
import software.aws.toolkits.jetbrains.core.executables.SemanticVersion
import software.aws.toolkits.jetbrains.core.executables.VersionRange
import software.aws.toolkits.jetbrains.settings.ExecutableDetector
import software.aws.toolkits.resources.message
import java.nio.file.Path
import java.nio.file.Paths

object SamExecutable2 : ExecutableType2<SemanticVersion>, AutoResolvable {
    val MIN_VERSION = SemanticVersion(SemVer("1.0.0", 1, 0, 0))
    val MAX_VERSION = SemanticVersion(SemVer("2.0.0", 2, 0, 0))

    override val displayName: String = "SAM CLI"
    override val id: String = "samCli"

    override fun determineVersion(path: Path): SemanticVersion {
        val output = ExecUtil.execAndGetOutput(GeneralCommandLine(path.toString(), "--info"))
        if (output.exitCode != 0) {
            throw IllegalStateException(output.stderr)
        }

        val tree = SamCommon.mapper.readTree(output.stdout)
        val version = tree.get(SamCommon.SAM_INFO_VERSION_KEY).asText()
        return SemanticVersion(
            SemVer.parseFromText(version) ?: throw IllegalStateException(message("executableCommon.version_parse_error", SamCommon.SAM_NAME, version))
        )
    }

    override fun supportedVersions(): List<VersionRange<SemanticVersion>> = listOf(VersionRange(MIN_VERSION, MAX_VERSION))

    override fun resolve(): Path? {
        val path = (
            if (SystemInfo.isWindows) {
                ExecutableDetector().find(
                    arrayOf("C:\\Program Files\\Amazon\\AWSSAMCLI\\bin", "C:\\Program Files (x86)\\Amazon\\AWSSAMCLI\\bin"),
                    arrayOf("sam.cmd", "sam.exe")
                )
            } else {
                ExecutableDetector().find(
                    arrayOf("/usr/local/bin", "/usr/bin"),
                    arrayOf("sam")
                )
            }
            ) ?: return null

        return Paths.get(path)
    }
}
