// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.util.io.createDirectories
import software.aws.toolkits.core.utils.deleteIfExists
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class ArtifactHelper(private val lspArtifactsPath: Path = DEFAULT_ARTIFACT_PATH) {

    companion object {
        private val DEFAULT_ARTIFACT_PATH = getToolkitsCommonCacheRoot().resolve("aws").resolve("toolkits").resolve("language-servers")
        private val logger = getLogger<ArtifactHelper>()
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private val currentAttempt = AtomicInteger(0)
    }

    fun removeDeListedVersions(deListedVersions: List<ManifestManager.Version>) {
        val localFolders: List<Path> = getSubFolders(lspArtifactsPath)

        deListedVersions.forEach { deListedVersion ->
            val versionToDelete = deListedVersion.serverVersion ?: return

            localFolders
                .filter { folder -> folder.fileName.toString() == versionToDelete }
                .forEach { folder ->
                    try {
                        folder.toFile().deleteRecursively()
                        logger.info { "Successfully deleted deListed version: ${folder.fileName}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to delete deListed version ${folder.fileName}: ${e.message}" }
                    }
                }
        }
    }

    fun getExistingLSPArtifacts(versions: List<ManifestManager.Version>, target: ManifestManager.VersionTarget?): Boolean {
        if (versions.isEmpty() || target?.contents == null) return false

        val localLSPPath = lspArtifactsPath.resolve(versions.first().serverVersion.toString())
        if (!localLSPPath.exists()) return false

        val hasInvalidFiles = target.contents.any { content ->
            content.filename?.let { filename ->
                val filePath = localLSPPath.resolve(filename)
                !filePath.exists() || generateMD5Hash(filePath) != content.hashes?.firstOrNull()
            } ?: false
        }

        if (hasInvalidFiles) {
            try {
                localLSPPath.toFile().deleteRecursively()
                logger.info { "Deleted mismatched LSP artifacts at: $localLSPPath" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete mismatched LSP artifacts at: $localLSPPath" }
            }
        }
        return hasInvalidFiles
    }

    fun tryDownloadLspArtifacts(versions: List<ManifestManager.Version>, target: ManifestManager.VersionTarget?) {
        val temporaryDownloadPath = lspArtifactsPath.resolve("temp")
        val downloadPath = lspArtifactsPath.resolve(versions.first().serverVersion.toString())

        while (currentAttempt.get() < MAX_DOWNLOAD_ATTEMPTS) {
            currentAttempt.incrementAndGet()
            logger.info { "Attempt ${currentAttempt.get()} of $MAX_DOWNLOAD_ATTEMPTS to download LSP artifacts" }

            try {
                if (downloadLspArtifacts(temporaryDownloadPath, target)) {
                    moveFilesFromSourceToDestination(temporaryDownloadPath, downloadPath)
                    logger.info { "Successfully downloaded and moved LSP artifacts to $downloadPath" }
                    return
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to download/move LSP artifacts on attempt ${currentAttempt.get()}" }
                temporaryDownloadPath.toFile().deleteRecursively()

                if (currentAttempt.get() >= MAX_DOWNLOAD_ATTEMPTS) {
                    throw LspException("Failed to download LSP artifacts after $MAX_DOWNLOAD_ATTEMPTS attempts", LspException.ErrorCode.DOWNLOAD_FAILED)
                }
            }
        }
    }

    private fun downloadLspArtifacts(downloadPath: Path, target: ManifestManager.VersionTarget?): Boolean {
        if (target == null || target.contents.isNullOrEmpty()) {
            logger.warn { "No target contents available for download" }
            return false
        }
        try {
            downloadPath.createDirectories()
            target.contents.forEach { content ->
                if (content.url == null || content.filename == null) {
                    logger.warn { "Missing URL or filename in content" }
                    return@forEach
                }
                val filePath = downloadPath.resolve(content.filename)
                val contentHash = content.hashes?.firstOrNull() ?: run {
                    logger.warn { "No hash available for ${content.filename}" }
                    return@forEach
                }
                downloadAndValidateFile(content.url, filePath, contentHash)
            }
            validateDownloadedFiles(downloadPath, target.contents)
        } catch (e: Exception) {
            logger.error(e) { "Failed to download LSP artifacts: ${e.message}" }
            downloadPath.toFile().deleteRecursively()
            return false
        }
        return true
    }

    private fun downloadAndValidateFile(url: String, filePath: Path, expectedHash: String) {
        try {
            if (!filePath.exists()) {
                logger.info { "Downloading file: ${filePath.fileName}" }
                saveFileFromUrl(url, filePath)
            }
            if (!validateFileHash(filePath, expectedHash)) {
                logger.warn { "Hash mismatch for ${filePath.fileName}, re-downloading" }
                filePath.deleteIfExists()
                saveFileFromUrl(url, filePath)
                if (!validateFileHash(filePath, expectedHash)) {
                    throw LspException("Hash mismatch after re-download for ${filePath.fileName}", LspException.ErrorCode.HASH_MISMATCH)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to download/validate file: ${filePath.fileName}", e)
        }
    }

    private fun validateFileHash(filePath: Path, expectedHash: String): Boolean = generateMD5Hash(filePath) == expectedHash

    private fun validateDownloadedFiles(downloadPath: Path, contents: List<ManifestManager.TargetContent>) {
        val missingFiles = contents
            .mapNotNull { it.filename }
            .filter { filename ->
                !downloadPath.resolve(filename).exists()
            }
        if (missingFiles.isNotEmpty()) {
            val errorMessage = "Missing required files: ${missingFiles.joinToString(", ")}"
            logger.error { errorMessage }
            throw LspException(errorMessage, LspException.ErrorCode.DOWNLOAD_FAILED)
        }
    }
}
