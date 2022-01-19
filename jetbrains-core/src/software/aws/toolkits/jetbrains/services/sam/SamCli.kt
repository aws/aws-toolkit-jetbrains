// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ssm

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.core.tools.BaseToolType
import software.aws.toolkits.jetbrains.core.tools.DocumentedToolType
import software.aws.toolkits.jetbrains.core.tools.ManagedToolType
import software.aws.toolkits.jetbrains.core.tools.SemanticVersion
import software.aws.toolkits.jetbrains.core.tools.Tool
import software.aws.toolkits.jetbrains.core.tools.ToolType
import software.aws.toolkits.jetbrains.core.tools.VersionRange
import software.aws.toolkits.jetbrains.core.tools.extractZip
import software.aws.toolkits.jetbrains.core.tools.findExe
import software.aws.toolkits.jetbrains.core.tools.until
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.utils.checkSuccess
import software.aws.toolkits.telemetry.ToolId
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

object SamCli : ManagedToolType<SemanticVersion>, DocumentedToolType<SemanticVersion>, BaseToolType<SemanticVersion>() {
    override val telemetryId: ToolId = ToolId.Unknown
    override val displayName: String = "AWS SAM CLI"

    override fun supportedVersions(): VersionRange<SemanticVersion> = SemanticVersion(1, 0, 0) until SemanticVersion(2, 0, 0)

    override fun downloadVersion(version: SemanticVersion, destinationDir: Path, indicator: ProgressIndicator?): Path? {
        // TODO: Move to CpuArch, FIX_WHEN_MIN_IS_211
        val downloadUrl = when {
            SystemInfo.isWindows -> windowsUrl(version)
            SystemInfo.isLinux -> linuxUrl(version)
            SystemInfo.isMac -> return null
            else -> throw IllegalStateException("Failed to find compatible SSM plugin: SystemInfo=${SystemInfo.OS_NAME}, Arch=${SystemInfo.OS_ARCH}")
        }

        val fileName = downloadUrl.substringAfterLast("/")
        val destination = destinationDir.resolve(fileName)

        saveFileFromUrl(downloadUrl, destination, indicator)

        return destination
    }

    override fun installVersion(version: SemanticVersion, downloadArtifact: Path?, destinationDir: Path, indicator: ProgressIndicator?) {
        // install mac
        if (downloadArtifact == null) {
            if (!SystemInfo.isMac) {
                throw IllegalStateException("Download artifact was not provided")
            }

            // PEP 394 seems to imply that the pythonX.Y command is available in all recent distributions (i.e. >= 3.6)
            // python3.8 is first since that is the version preferred by SAM CLI
            val python = sequenceOf("python3.8", "python3.10", "python3.9", "python3", "python").mapNotNull {
                PathEnvironmentVariableUtil.findInPath(it)
            }.firstOrNull()?.absolutePath
                // don't want to be in the business of installing python for mac users
                // TODO: localize
                ?: throw RuntimeException("python3 not available in path")

            runInstall(GeneralCommandLine(python, "-m", "venv", destinationDir.toAbsolutePath().toString()))
            runInstall(GeneralCommandLine("$destinationDir/bin/pip", "install", "--upgrade", "pip"))
            runInstall(GeneralCommandLine("$destinationDir/bin/pip", "install", "-v", "--ignore-installed", "aws-sam-cli==${version.displayValue()}"))

            return
        }

        when (downloadArtifact.fileName.toString().substringAfterLast(".")) {
            // linux
            "zip" -> {
                val tmp = Files.createTempDirectory(downloadArtifact.fileName.toString())
                val destination = destinationDir.toAbsolutePath().toString()
                extractZip(downloadArtifact, tmp)
                runInstall(GeneralCommandLine("$tmp/install", "--update", "--install-dir", destination, "--bin-dir", destination))
            }
            // windows
            "exe" -> runInstall(
                GeneralCommandLine(
                    "start",
                    "/wait",
                    "msiexec.exe",
                    "/a",
                    downloadArtifact.toString(),
                    "/quiet",
                    // don't add to add/remove programs
                    "ARPSYSTEMCOMPONENT=1",
                    "TARGETDIR=$destinationDir"
                )
            )
        }
    }

    override fun determineLatestVersion(): SemanticVersion = SemanticVersion.parse(
        HttpRequests.head(LATEST_POINTER)
            .connect {
                it.connection.url.path.substringAfterLast('/').substringAfter('v')
            }
    )

    override fun versionCommand(baseCmd: GeneralCommandLine) {
        baseCmd.withParameters("--info")
    }

    override fun parseVersion(output: String): SemanticVersion =
        SemanticVersion.parse(
            SamCommon.mapper.readTree(output)
                .get(SamCommon.SAM_INFO_VERSION_KEY)
                .asText()
        )

    override fun toTool(installDir: Path): Tool<ToolType<SemanticVersion>> {
        val executableName = if (SystemInfo.isWindows) {
            "sam.bat"
        } else {
            "sam"
        }

        return findExe(executableName, installDir)
    }

    override fun documentationUrl() =
        "https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html"

    @VisibleForTesting
    fun windowsUrl(version: SemanticVersion) = "$BASE_URL/download/v${version.displayValue()}/AWS_SAM_CLI_64_PY3.msi"

    @VisibleForTesting
    fun linuxUrl(version: SemanticVersion) = "$BASE_URL/download/v${version.displayValue()}/aws-sam-cli-linux-x86_64.zip"

    private fun runInstall(cmd: GeneralCommandLine) {
        val processOutput = ExecUtil.execAndGetOutput(cmd, INSTALL_TIMEOUT.toMillis().toInt())

        if (!processOutput.checkSuccess(LOGGER)) {
            throw IllegalStateException("Failed to install $displayName\nSTDOUT:${processOutput.stdout}\nSTDERR:${processOutput.stderr}")
        }
    }

    private val LOGGER = getLogger<SsmPlugin>()
    private const val BASE_URL = "https://github.com/aws/aws-sam-cli/releases"
    private const val LATEST_POINTER = "$BASE_URL/latest"
    private val INSTALL_TIMEOUT = Duration.ofSeconds(30)
}
