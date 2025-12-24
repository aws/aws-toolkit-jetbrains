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
import software.amazon.q.core.utils.deleteIfExists
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.saveFileFromUrl
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.amazon.q.resources.AwsCoreBundle
import software.aws.toolkits.telemetry.LanguageServerSetupStage
import software.aws.toolkits.telemetry.Telemetry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

class ArtifactHelper(private val lspArtifactsPath: Path = DEFAULT_ARTIFACT_PATH, private val maxDownloadAttempts: Int = MAX_DOWNLOAD_ATTEMPTS) {

    companion object {
        private val DEFAULT_ARTIFACT_PATH = getToolkitsCommonCacheRoot().resolve(
            Paths.get("aws", "toolkits", "language-servers", "AmazonQ-JetBrains-temp")
        )
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
                    if (semVer >= manifestVersionRanges.startVersion && semVer < manifestVersionRanges.endVersion) {
                        localFolder to semVer
                    } else {
                        null
                    }
                }
            }
            .sortedByDescending { (_, semVer) -> semVer }
    }

    fun getExistingLspArtifacts(targetVersion: Version, target: VersionTarget): Boolean {
        if (target.contents == null) return false

        val localLSPPath = lspArtifactsPath.resolve(targetVersion.serverVersion.toString())
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

    suspend fun tryDownloadLspArtifacts(project: Project, targetVersion: Version, target: VersionTarget): Path? {
        val destinationPath = lspArtifactsPath.resolve(targetVersion.serverVersion.toString())

        while (currentAttempt.get() < maxDownloadAttempts) {
            currentAttempt.incrementAndGet()
            logger.info { "Attempt ${currentAttempt.get()} of $maxDownloadAttempts to download LSP artifacts" }
            val temporaryDownloadPath = Files.createTempDirectory("lsp-dl")

            try {
                return withBackgroundProgress(
                    project,
                    AwsCoreBundle.message("amazonqFeatureDev.placeholder.downloading_and_extracting_lsp_artifacts"),
                    cancellable = true
                ) {
                    if (downloadLspArtifacts(project, temporaryDownloadPath, target) && !target.contents.isNullOrEmpty()) {
                        moveFilesFromSourceToDestination(temporaryDownloadPath, destinationPath)
                        target.contents
                            .mapNotNull { it.filename }
                            .forEach { filename -> extractZipFile(destinationPath.resolve(filename), destinationPath) }
                        logger.info { "Successfully downloaded and moved LSP artifacts to $destinationPath" }

                        val thirdPartyLicenses = targetVersion.thirdPartyLicenses
                        logger.info {
                            "Installing Amazon Q Language Server v${targetVersion.serverVersion} to: $destinationPath. " +
                                if (thirdPartyLicenses == null) "" else "Attribution notice can be found at $thirdPartyLicenses"
                        }

                        return@withBackgroundProgress destinationPath
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
                destinationPath.toFile().deleteRecursively()
            }
        }
        logger.error { "Failed to download LSP artifacts after $maxDownloadAttempts attempts" }
        return null
    }

    @VisibleForTesting
    internal fun downloadLspArtifacts(project: Project, downloadPath: Path, target: VersionTarget?): Boolean {
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
                downloadAndValidateFile(project, content.url, filePath, contentHash)
            }
            validateDownloadedFiles(downloadPath, target.contents)
        } catch (e: Exception) {
            logger.error(e) { "Failed to download LSP artifacts: ${e.message}" }
            downloadPath.toFile().deleteRecursively()
            return false
        }
        return true
    }

    private fun downloadAndValidateFile(project: Project, url: String, filePath: Path, expectedHash: String) {
        val recordDownload = { runnable: () -> Unit ->
            Telemetry.languageserver.setup.use { telemetry ->
                telemetry.id("q")
                telemetry.languageServerSetupStage(LanguageServerSetupStage.GetServer)
                telemetry.metadata("credentialStartUrl", getStartUrl(project))
                telemetry.success(true)

                try {
                    runnable()
                } catch (t: Throwable) {
                    telemetry.success(false)
                    telemetry.recordException(t)
                }
            }
        }

        try {
            if (!filePath.exists()) {
                logger.info { "Downloading file: ${filePath.fileName}" }
                recordDownload { saveFileFromUrl(url, filePath, ProgressManager.getInstance().progressIndicator) }
            }
            if (!validateFileHash(filePath, expectedHash)) {
                logger.warn { "Hash mismatch for ${filePath.fileName}, re-downloading" }
                filePath.deleteIfExists()
                recordDownload { saveFileFromUrl(url, filePath) }

                Telemetry.languageserver.setup.use {
                    it.id("q")
                    it.languageServerSetupStage(LanguageServerSetupStage.Validate)
                    it.metadata("credentialStartUrl", getStartUrl(project))
                    it.success(true)

                    if (!validateFileHash(filePath, expectedHash)) {
                        it.success(false)

                        val exception = LspException("Hash mismatch after re-download for ${filePath.fileName}", LspException.ErrorCode.HASH_MISMATCH)
                        it.recordException(exception)
                        throw exception
                    }
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
