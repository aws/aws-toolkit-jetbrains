// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.createDirectories
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CoroutineScope
import org.apache.commons.codec.digest.DigestUtils
import software.amazon.awssdk.utils.UserHomeDirectoryUtils
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryDirOp
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.lsp.EncoderServer2
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.extractZipFile
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

class EncoderServer(val project: Project, private val cs: CoroutineScope) : Disposable {
    val cachePath = Paths.get(
        UserHomeDirectoryUtils.userHomeDirectory()
    ).resolve(".aws").resolve("amazonq").resolve("cache")
    val manifestManager = ManifestManager()
    private val serverDirectoryName = "qserver-${manifestManager.currentVersion}.zip"
    val port by lazy { NetUtils.findAvailableSocketPort() }
    private val nodeRunnableName = if (manifestManager.getOs() == "windows") "node.exe" else "node"
    lateinit var encoderServer2: EncoderServer2

    fun downloadArtifactsAndStartServer() {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }
        downloadArtifactsIfNeeded()
        start()
    }

    fun isNodeProcessRunning() = encoderServer2.initializer.isCompleted

    private fun getCommand(): GeneralCommandLine {
        val threadCount = CodeWhispererSettings.getInstance().getProjectContextIndexThreadCount()
        val isGpuEnabled = CodeWhispererSettings.getInstance().isProjectContextGpu()
        val environment = buildMap {
            if (threadCount > 0) {
                put("Q_WORKER_THREADS", threadCount.toString())
            }

            if (isGpuEnabled) {
                put("Q_ENABLE_GPU", "true")
            }
        }

        val jsPath = cachePath.resolve("qserver").resolve("lspServer.js").toString()
        val nodePath = cachePath.resolve(nodeRunnableName).toString()
        val command = GeneralCommandLine(nodePath, jsPath)
            .withParameters("--stdio")
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(environment)
        return command
    }

    fun start() {
        encoderServer2 = EncoderServer2(this, getCommand(), project, cs)
    }

    private fun close() {
        Disposer.dispose(encoderServer2)
    }

    private fun downloadArtifactsIfNeeded() {
        cachePath.tryDirOp(logger) { createDirectories() }
        val nodePath = cachePath.resolve(nodeRunnableName)
        val zipFilePath = cachePath.resolve(serverDirectoryName)
        val manifest = manifestManager.getManifest() ?: return
        try {
            if (!Files.exists(nodePath)) {
                val nodeContent = manifestManager.getNodeContentFromManifest(manifest)
                if (nodeContent?.url != null) {
                    val bytes = HttpRequests.request(nodeContent.url).readBytes(null)
                    if (validateHash(nodeContent.hashes?.first(), bytes)) {
                        downloadFromRemote(nodeContent.url, nodePath)
                    }
                }
            }
            if (manifestManager.currentOs != "windows") {
                makeFileExecutable(nodePath)
            }
            val files = cachePath.toFile().listFiles()
            if (files.isNotEmpty()) {
                val filenames = files.map { it.name }
                if (filenames.contains(serverDirectoryName)) {
                    return
                }
                tryDeleteOldArtifacts(filenames)
            }

            val serverContent = manifestManager.getZipContentFromManifest(manifest)
            if (serverContent?.url != null) {
                if (validateHash(serverContent.hashes?.first(), HttpRequests.request(serverContent.url).readBytes(null))) {
                    downloadFromRemote(serverContent.url, zipFilePath)
                    extractZipFile(zipFilePath, cachePath)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "error downloading artifacts:" }
        }
    }

    private fun tryDeleteOldArtifacts(filenames: List<String>) {
        try {
            filenames.forEach { filename ->
                if (filename.contains("qserver")) {
                    val parts = filename.split("-")
                    val version = if (parts.size > 1) parts[1] else null
                    if (version != null && version != "${manifestManager.currentVersion}.zip") {
                        Files.deleteIfExists(cachePath.resolve(filename))
                        Files.deleteIfExists(cachePath.resolve("qserver"))
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "error deleting old artifacts:" }
        }
    }

    fun validateHash(expectedHash: String?, input: ByteArray): Boolean {
        if (expectedHash == null) { return false }
        val sha384 = DigestUtils.sha384Hex(input)
        val isValid = ("sha384:$sha384") == expectedHash
        if (!isValid) {
            logger.warn { "failed validating hash for artifacts $expectedHash" }
        }
        return isValid
    }

    private fun makeFileExecutable(filePath: Path) {
        val permissions = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE,
        )
        Files.setPosixFilePermissions(filePath, permissions)
    }

    private fun downloadFromRemote(url: String, path: Path) {
        try {
            HttpRequests.request(url).saveToFile(path, null)
        } catch (e: IOException) {
            logger.warn { "error downloading from remote ${e.message}" }
        }
    }

    override fun dispose() {
        close()
    }

    companion object {
        private val logger = getLogger<EncoderServer>()
    }
}
