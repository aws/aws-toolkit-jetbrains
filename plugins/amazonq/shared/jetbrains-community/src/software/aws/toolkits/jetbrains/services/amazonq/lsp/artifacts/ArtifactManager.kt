// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.util.text.SemVer
import org.assertj.core.util.VisibleForTesting
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager

class ArtifactManager {

    data class SupportedManifestVersionRange(
        val startVersion: SemVer,
        val endVersion: SemVer,
    )
    data class LSPVersions(
        val deListedVersions: List<ManifestManager.Version>,
        val inRangeVersions: List<ManifestManager.Version>,
    )

    private val manifestFetcher: ManifestFetcher
    private val artifactHelper: ArtifactHelper
    private val manifestVersionRanges: SupportedManifestVersionRange

    // Primary constructor with config
    constructor(
        manifestFetcher: ManifestFetcher = ManifestFetcher(),
        artifactFetcher: ArtifactHelper = ArtifactHelper(),
        manifestRange: SupportedManifestVersionRange?,
    ) {
        manifestVersionRanges = manifestRange ?: DEFAULT_VERSION_RANGE
        this.manifestFetcher = manifestFetcher
        this.artifactHelper = artifactFetcher
    }

    // Secondary constructor with no parameters
    constructor() : this(ManifestFetcher(), ArtifactHelper(), null)

    companion object {
        private val DEFAULT_VERSION_RANGE = SupportedManifestVersionRange(
            startVersion = SemVer("3.0.0", 3, 0, 0),
            endVersion = SemVer("4.0.0", 4, 0, 0)
        )
        private val logger = getLogger<ArtifactManager>()
    }

    fun fetchArtifact() {
        val manifest = manifestFetcher.fetch() ?: throw LspException(
            "Language Support is not available, as manifest is missing.",
            LspException.ErrorCode.MANIFEST_FETCH_FAILED
        )
        val lspVersions = getLSPVersionsFromManifestWithSpecifiedRange(manifest)

        this.artifactHelper.removeDeListedVersions(lspVersions.deListedVersions)

        if (lspVersions.inRangeVersions.isEmpty()) {
            // No versions are found which are in the given range.
            throw LspException("Language server versions not found in manifest.", LspException.ErrorCode.NO_COMPATIBLE_LSP_VERSION)
        }

        // If there is an LSP Manifest with the same version
        val target = getTargetFromLspManifest(lspVersions.inRangeVersions)

        // Get Local LSP files and check if we can re-use existing LSP Artifacts
        if (this.artifactHelper.getExistingLSPArtifacts(lspVersions.inRangeVersions, target)) {
            return
        }

        this.artifactHelper.tryDownloadLspArtifacts(lspVersions.inRangeVersions, target)
        logger.info { "Success" }
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
            throw LspException("Target not found in the current Version: ${versions.first().serverVersion}", LspException.ErrorCode.TARGET_NOT_FOUND)
        }
        return currentTarget
    }
}
