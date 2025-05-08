// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.io.createDirectories
import com.intellij.util.text.SemVer
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.deleteIfExists
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.exists
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.resources.AwsCoreBundle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

class ArtifactHelper(private val lspArtifactsPath: Path = DEFAULT_ARTIFACT_PATH, private val maxDownloadAttempts: Int = MAX_DOWNLOAD_ATTEMPTS) {

    companion object {
        private val DEFAULT_ARTIFACT_PATH = getToolkitsCommonCacheRoot().resolve(Paths.get("aws", "toolkits", "language-servers", "AmazonQ-JetBrains-temp"))
        private val logger = getLogger<ArtifactHelper>()
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
    }
    private val currentAttempt = AtomicInteger(0)

    fun removeDelistedVersions(delistedVersions: List<Version>) {
        val localFolders = getSubFolders(lspArtifactsPath)

        delistedVersions.forEach { delistedVersion ->
            val versionToDelete = delistedVersion.serverVersion ?: return@forEach

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

    fun deleteOlderLspArtifacts(manifestVersionRanges: ArtifactManager.SupportedManifestVersionRange) {
        val validVersions = getAllLocalLspArtifactsWithinManifestRange(manifestVersionRanges)

        // Keep the latest 2 versions, delete others
        validVersions.drop(2).forEach { (folder, _) ->
            try {
                folder.toFile().deleteRecursively()
                logger.info { "Deleted older LSP artifact: ${folder.fileName}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete older LSP artifact: ${folder.fileName}" }
            }
        }
    }

    fun getAllLocalLspArtifactsWithinManifestRange(manifestVersionRanges: ArtifactManager.SupportedManifestVersionRange): List<Pair<Path, SemVer>> {
        val localFolders = getSubFolders(lspArtifactsPath)

        return localFolders
            .mapNotNull { localFolder ->
                SemVer.parseFromText(localFolder.fileName.toString())?.let { semVer ->
                    if (semVer in manifestVersionRanges.startVersion..manifestVersionRanges.endVersion) {
                        localFolder to semVer
                    } else {
                        null
                    }
                }
            }
            .sortedByDescending { (_, semVer) -> semVer }
    }

    fun getExistingLspArtifacts(versions: List<Version>, target: VersionTarget?): Boolean {
        if (versions.isEmpty() || target?.contents == null) return false

        val localLSPPath = lspArtifactsPath.resolve(versions.first().serverVersion.toString())
        if (!localLSPPath.exists()) return false

        val hasInvalidFiles = target.contents.any { content ->
            content.filename?.let { filename ->
                val filePath = localLSPPath.resolve(filename)
                !filePath.exists() || !validateFileHash(filePath, content.hashes?.firstOrNull())
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
        return !hasInvalidFiles
    }

    suspend fun tryDownloadLspArtifacts(project: Project, versions: List<Version>, target: VersionTarget?): Path? {
        val temporaryDownloadPath = Files.createTempDirectory("lsp-dl")
        val downloadPath = lspArtifactsPath.resolve(versions.first().serverVersion.toString())

        while (currentAttempt.get() < maxDownloadAttempts) {
            currentAttempt.incrementAndGet()
            logger.info { "Attempt ${currentAttempt.get()} of $maxDownloadAttempts to download LSP artifacts" }

            try {
                return withBackgroundProgress(
                    project,
                    AwsCoreBundle.message("amazonqFeatureDev.placeholder.downloading_and_extracting_lsp_artifacts"),
                    cancellable = true
                ) {
                    if (downloadLspArtifacts(temporaryDownloadPath, target) && target != null && !target.contents.isNullOrEmpty()) {
                        moveFilesFromSourceToDestination(temporaryDownloadPath, downloadPath)
                        target.contents
                            .mapNotNull { it.filename }
                            .forEach { filename -> extractZipFile(downloadPath.resolve(filename), downloadPath) }
                        logger.info { "Successfully downloaded and moved LSP artifacts to $downloadPath" }

                        return@withBackgroundProgress downloadPath
                    }

                    return@withBackgroundProgress null
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> {
                        logger.error(e) { "User cancelled download and extracting of LSP artifacts.." }
                        currentAttempt.set(maxDownloadAttempts) // To exit the while loop.
                    }
                    else -> { logger.error(e) { "Failed to download/move LSP artifacts on attempt ${currentAttempt.get()}" } }
                }
                temporaryDownloadPath.toFile().deleteRecursively()
                downloadPath.toFile().deleteRecursively()
            }
        }
        logger.error { "Failed to download LSP artifacts after $maxDownloadAttempts attempts" }
        return null
    }

    @VisibleForTesting
    internal fun downloadLspArtifacts(downloadPath: Path, target: VersionTarget?): Boolean {
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
                saveFileFromUrl(url, filePath, ProgressManager.getInstance().progressIndicator)
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

    @VisibleForTesting
    internal fun validateFileHash(filePath: Path, expectedHash: String?): Boolean {
        if (expectedHash == null) return false
        val contentHash = generateSHA384Hash(filePath)
        return "sha384:$contentHash" == expectedHash
    }

    private fun validateDownloadedFiles(downloadPath: Path, contents: List<TargetContent>) {
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
