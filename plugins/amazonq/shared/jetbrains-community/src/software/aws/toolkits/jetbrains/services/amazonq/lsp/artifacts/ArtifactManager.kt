// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.project.Project
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path

class ArtifactManager(
    private val project: Project,
    private val manifestFetcher: ManifestFetcher = ManifestFetcher(),
    private val artifactHelper: ArtifactHelper = ArtifactHelper(),
    manifestRange: SupportedManifestVersionRange?,
) {

    data class SupportedManifestVersionRange(
        val startVersion: SemVer,
        val endVersion: SemVer,
    )
    data class LSPVersions(
        val deListedVersions: List<ManifestManager.Version>,
        val inRangeVersions: List<ManifestManager.Version>,
    )

    private val manifestVersionRanges: SupportedManifestVersionRange = manifestRange ?: DEFAULT_VERSION_RANGE

    companion object {
        private val DEFAULT_VERSION_RANGE = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )
        private val logger = getLogger<ArtifactManager>()
    }

    suspend fun fetchArtifact(): Path {
        val manifest = manifestFetcher.fetch() ?: throw LspException(
            "Language Support is not available, as manifest is missing.",
            LspException.ErrorCode.MANIFEST_FETCH_FAILED
        )
        val lspVersions = getLSPVersionsFromManifestWithSpecifiedRange(manifest)

        this.artifactHelper.removeDelistedVersions(lspVersions.deListedVersions)

        if (lspVersions.inRangeVersions.isEmpty()) {
            // No versions are found which are in the given range. Fallback to local lsp artifacts.
            val localLspArtifacts = this.artifactHelper.getAllLocalLspArtifactsWithinManifestRange(manifestVersionRanges)
            if (localLspArtifacts.isNotEmpty()) {
                return localLspArtifacts.first().first
            }
            throw LspException("Language server versions not found in manifest.", LspException.ErrorCode.NO_COMPATIBLE_LSP_VERSION)
        }

        // If there is an LSP Manifest with the same version
        val target = getTargetFromLspManifest(lspVersions.inRangeVersions)
        // Get Local LSP files and check if we can re-use existing LSP Artifacts
        val artifactPath: Path = if (this.artifactHelper.getExistingLspArtifacts(lspVersions.inRangeVersions, target)) {
            this.artifactHelper.getAllLocalLspArtifactsWithinManifestRange(manifestVersionRanges).first().first
        } else {
            this.artifactHelper.tryDownloadLspArtifacts(project, lspVersions.inRangeVersions, target)
                ?: throw LspException("Failed to download LSP artifacts", LspException.ErrorCode.DOWNLOAD_FAILED)
        }
        this.artifactHelper.deleteOlderLspArtifacts(manifestVersionRanges)
        return artifactPath
    }

    @VisibleForTesting
    internal fun getLSPVersionsFromManifestWithSpecifiedRange(manifest: ManifestManager.Manifest): LSPVersions {
        if (manifest.versions.isNullOrEmpty()) return LSPVersions(emptyList(), emptyList())

        val (deListed, inRange) = manifest.versions.mapNotNull { version ->
            version.serverVersion?.let { serverVersion ->
                SemVer.parseFromText(serverVersion)?.let { semVer ->
                    when {
                        version.isDelisted != false -> Pair(version, true) // Is deListed
                        semVer in manifestVersionRanges.startVersion..manifestVersionRanges.endVersion -> Pair(version, false) // Is in range
                        else -> null
                    }
                }
            }
        }.partition { it.second }

        return LSPVersions(
            deListedVersions = deListed.map { it.first },
            inRangeVersions = inRange.map { it.first }.sortedByDescending { (_, semVer) -> semVer }
        )
    }

    private fun getTargetFromLspManifest(versions: List<ManifestManager.Version>): ManifestManager.VersionTarget {
        val currentOS = getCurrentOS()
        val currentArchitecture = getCurrentArchitecture()

        val currentTarget = versions.first().targets?.find { target ->
            target.platform == currentOS && target.arch == currentArchitecture
        }
        if (currentTarget == null) {
            logger.error { "Failed to obtain target for $currentOS and $currentArchitecture" }
            throw LspException("Target not found in the current Version: ${versions.first().serverVersion}", LspException.ErrorCode.TARGET_NOT_FOUND)
        }
        logger.info { "Target found in the current Version: ${versions.first().serverVersion}" }
        return currentTarget
    }
}
