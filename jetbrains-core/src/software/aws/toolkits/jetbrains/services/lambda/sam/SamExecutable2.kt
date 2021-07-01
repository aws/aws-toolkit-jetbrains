// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.SemVer
import software.aws.toolkits.jetbrains.core.executables.AutoResolvable
import software.aws.toolkits.jetbrains.core.executables.ExecutableCommon
import software.aws.toolkits.jetbrains.core.executables.ExecutableType2
import software.aws.toolkits.jetbrains.core.executables.SemanticVersion
import software.aws.toolkits.jetbrains.core.executables.VersionRange
import software.aws.toolkits.jetbrains.settings.ExecutableDetector
import java.nio.file.Path
import java.nio.file.Paths

class SamExecutable2 : ExecutableType2<SemanticVersion>, AutoResolvable {
    companion object {
        // inclusive
        val minVersion = SemVer("1.0.0", 1, 0, 0)

        // exclusive
        val maxVersion = SemVer("2.0.0", 2, 0, 0)
    }

    override val displayName: String = "sam"
    override val id: String = "samCli"

    override fun determineVersion(path: Path) = SemanticVersion(
        ExecutableCommon.getVersion(
            path.toString(),
            SamVersionCache,
            this.displayName
        )
    )

    override fun supportedVersions(): List<VersionRange<SemanticVersion>> = listOf(
        VersionRange(
            SemanticVersion(minVersion),
            SemanticVersion(maxVersion)
        )
    )

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
