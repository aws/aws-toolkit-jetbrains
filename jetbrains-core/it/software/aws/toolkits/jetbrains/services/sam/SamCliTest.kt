// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sam

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.util.io.HttpRequests
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.jetbrains.services.ssm.SamCli

class SamCliTest {
    @Rule
    @JvmField
    val application = ApplicationRule()

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `download URLs all work`() {
        val latest = SamCli.determineLatestVersion()
        SoftAssertions.assertSoftly { softly ->
            listOf(
                SamCli.windowsUrl(latest),
                SamCli.linuxUrl(latest)
            ).forEach { url ->
                softly.assertThatCode { HttpRequests.head(url).tryConnect() }.doesNotThrowAnyException()
            }
        }
    }

    @Test
    fun `end to end install works`() {
        val executableName = if (SystemInfo.isWindows) {
            "sam.bat"
        } else {
            "sam"
        }

        val latest = SamCli.determineLatestVersion()
        val downloadDir = tempFolder.newFolder().toPath()
        val installDir = tempFolder.newFolder().toPath()

        val downloadedFile = SamCli.downloadVersion(latest, downloadDir, null)
        SamCli.installVersion(latest, downloadedFile, installDir, null)
        val tool = SamCli.toTool(installDir)
        assertThat(tool.path.fileName.toString()).isEqualTo(executableName)

        val reportedLatest = SamCli.determineVersion(tool.path)
        assertThat(reportedLatest).isEqualTo(latest)
    }
}
