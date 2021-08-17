// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import software.aws.toolkits.jetbrains.core.executables.AutoResolvable
import software.aws.toolkits.jetbrains.core.tools.SemanticVersion
import software.aws.toolkits.jetbrains.core.tools.ToolType
import software.aws.toolkits.jetbrains.core.tools.VersionRange
import software.aws.toolkits.jetbrains.core.tools.until
import software.aws.toolkits.jetbrains.settings.ExecutableDetector
import java.nio.file.Path
import java.nio.file.Paths

object SamCli : ToolType<SemanticVersion>, AutoResolvable {
    private val MIN_VERSION = SemanticVersion(1, 0, 0)
    private val MAX_VERSION = SemanticVersion(2, 0, 0)

    // The minimum SAM CLI version required for images. TODO remove when sam min > 1.13.0
    val MIN_IMAGE_VERSION = SemanticVersion(1, 13, 0)

    override val displayName: String = "SAM CLI"
    override val id: String = "samCli"

    override fun determineVersion(path: Path): SemanticVersion {
        val output = ExecUtil.execAndGetOutput(GeneralCommandLine(path.toString(), "--info"))
        if (output.exitCode != 0) {
            throw IllegalStateException(output.stderr)
        }

        val tree = jacksonObjectMapper().readTree(output.stdout)
        val version = tree.get(SamCommon.SAM_INFO_VERSION_KEY).asText()
        return SemanticVersion.parse(version)
    }

    override fun supportedVersions(): List<VersionRange<SemanticVersion>> = listOf(MIN_VERSION until MAX_VERSION)

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
