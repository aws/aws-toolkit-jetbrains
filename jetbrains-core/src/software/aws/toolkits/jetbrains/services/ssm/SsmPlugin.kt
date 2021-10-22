// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ssm

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.Decompressor
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.getTextFromUrl
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.core.tools.FourPartVersion
import software.aws.toolkits.jetbrains.core.tools.ManagedToolType
import software.aws.toolkits.jetbrains.core.tools.Tool
import software.aws.toolkits.jetbrains.core.tools.ToolType
import software.aws.toolkits.jetbrains.core.tools.VersionRange
import software.aws.toolkits.jetbrains.core.tools.until
import software.aws.toolkits.jetbrains.utils.checkSuccess
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

object SsmPlugin : ManagedToolType<FourPartVersion> {
    private val isUbuntu by lazy {
        ExecUtil.execAndGetOutput(GeneralCommandLine("uname", "-a"), VERSION_TIMEOUT.toMillis().toInt()).stdout.contains("Ubuntu", ignoreCase = true)
    }
    override val id: String = "SSM-Plugin"
    override val displayName: String = "AWS Session Manager Plugin"

    override fun supportedVersions(): VersionRange<FourPartVersion> = FourPartVersion(1, 2, 0, 0) until FourPartVersion(2, 0, 0, 0)

    override fun determineVersion(path: Path): FourPartVersion {
        val processOutput = ExecUtil.execAndGetOutput(
            GeneralCommandLine(path.toString(), "--version"),
            VERSION_TIMEOUT.toMillis().toInt()
        )

        if (!processOutput.checkSuccess(LOGGER)) {
            throw IllegalStateException("Failed to determine version of $displayName")
        }

        return FourPartVersion.parse(processOutput.stdout.trim())
    }

    override fun downloadVersion(version: FourPartVersion, destinationDir: Path, indicator: ProgressIndicator?): Path {
        // TODO: Move to CpuArch, FIX_WHEN_MIN_IS_211
        val downloadUrl = when {
            SystemInfo.isWindows -> windowsUrl(version)
            SystemInfo.isMac -> macUrl(version)
            SystemInfo.isLinux && isUbuntu && SystemInfo.isArm64 -> ubuntuArm64(version)
            SystemInfo.isLinux && isUbuntu && SystemInfo.isIntel64 -> ubuntuI64(version)
            SystemInfo.isLinux && SystemInfo.isArm64 -> linuxArm64(version)
            SystemInfo.isLinux && SystemInfo.isIntel64 -> linuxI64(version)
            else -> throw IllegalStateException("Failed to find compatible SSM plugin: SystemInfo=${SystemInfo.OS_NAME}, Arch=${SystemInfo.OS_ARCH}")
        }

        val fileName = downloadUrl.substringAfterLast("/")
        val destination = destinationDir.resolve(fileName)

        saveFileFromUrl(downloadUrl, destination, indicator)

        return destination
    }

    // Visible for test
    internal fun windowsUrl(version: FourPartVersion) = "$BASE_URL/${version.displayValue()}/windows/SessionManagerPlugin.zip"
    internal fun macUrl(version: FourPartVersion) = "$BASE_URL/${version.displayValue()}/mac/sessionmanager-bundle.zip"
    internal fun ubuntuArm64(version: FourPartVersion) = "$BASE_URL/${version.displayValue()}/ubuntu_arm64/session-manager-plugin.deb"
    internal fun ubuntuI64(version: FourPartVersion) = "$BASE_URL/${version.displayValue()}/ubuntu_64bit/session-manager-plugin.deb"
    internal fun linuxArm64(version: FourPartVersion) = "$BASE_URL/${version.displayValue()}/linux_arm64/session-manager-plugin.rpm"
    internal fun linuxI64(version: FourPartVersion) = "$BASE_URL/${version.displayValue()}/linux_64bit/session-manager-plugin.rpm"

    override fun installVersion(downloadArtifact: Path, destinationDir: Path, indicator: ProgressIndicator?) {
        when (val extension = downloadArtifact.fileName.toString().substringAfterLast(".")) {
            "zip" -> Decompressor.Zip(downloadArtifact).withZipExtensions().extract(destinationDir)
            "rpm" -> runInstall(GeneralCommandLine("sh", "-c", "rpm2cpio $downloadArtifact | cpio -D $destinationDir -idmv"))
            "deb" -> runInstall(GeneralCommandLine("dpkg-deb", "-x", downloadArtifact.toString(), destinationDir.toString()))
            else -> throw IllegalStateException("Unknown extension $extension")
        }
    }

    private fun runInstall(cmd: GeneralCommandLine) {
        val processOutput = ExecUtil.execAndGetOutput(cmd, INSTALL_TIMEOUT.toMillis().toInt())

        if (!processOutput.checkSuccess(LOGGER)) {
            throw IllegalStateException("Failed to extract $displayName")
        }
    }

    override fun determineLatestVersion(): FourPartVersion = FourPartVersion.parse(getTextFromUrl(VERSION_FILE))

    override fun toTool(installDir: Path): Tool<ToolType<FourPartVersion>> {
        val executable = if (SystemInfo.isWindows) {
            "session-manager-plugin.exe"
        } else {
            "session-manager-plugin"
        }

        val executablePath = Path.of("sessionmanager-bundle", "bin", executable)
        val fullExecutablePath = installDir.resolve(executablePath).takeIf { Files.isExecutable(it) }
            ?: throw IllegalStateException("Failed to locate $executablePath under $installDir")
        return Tool(this, fullExecutablePath)
    }

    private val LOGGER = getLogger<SsmPlugin>()
    private const val BASE_URL = "https://s3.us-east-1.amazonaws.com/session-manager-downloads/plugin"
    private const val VERSION_FILE = "$BASE_URL/latest/VERSION"
    private val VERSION_TIMEOUT = Duration.ofSeconds(5)
    private val INSTALL_TIMEOUT = Duration.ofSeconds(30)
}
