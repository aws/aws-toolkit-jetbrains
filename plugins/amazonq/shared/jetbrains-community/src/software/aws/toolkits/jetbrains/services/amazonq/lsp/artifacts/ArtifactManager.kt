// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.text.SemVer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.amazonq.project.manifest.ManifestManager
import java.nio.file.Path

@Service
class ArtifactManager @NonInjectable internal constructor(private val manifestFetcher: ManifestFetcher, private val artifactHelper: ArtifactHelper) {
    constructor() : this(
        ManifestFetcher(),
        ArtifactHelper()
    )

    // we currently cannot handle the versions swithing in the middle of a user's session
    private val mutex = Mutex()
    private var artifactDeferred: Deferred<Path>? = null

    data class SupportedManifestVersionRange(
        val startVersion: SemVer,
        val endVersion: SemVer,
    )
    data class LSPVersions(
        val deListedVersions: List<ManifestManager.Version>,
        val inRangeVersions: List<ManifestManager.Version>,
    )

    companion object {
        private val DEFAULT_VERSION_RANGE = SupportedManifestVersionRange(
            startVersion = SemVer("0.0.0", 0, 0, 0),
            endVersion = SemVer("2.0.0", 2, 0, 0)
        )
        private val logger = getLogger<ArtifactManager>()
    }

    suspend fun fetchArtifact(project: Project): Path {
        mutex.withLock { artifactDeferred }?.let {
            return it.await()
        }

        return mutex.withLock {
            coroutineScope {
                async {
                    val manifest = manifestFetcher.fetch() ?: throw LspException(
                        "Language Support is not available, as manifest is missing.",
                        LspException.ErrorCode.MANIFEST_FETCH_FAILED
                    )
                    val lspVersions = getLSPVersionsFromManifestWithSpecifiedRange(manifest)

                    artifactHelper.removeDelistedVersions(lspVersions.deListedVersions)

                    if (lspVersions.inRangeVersions.isEmpty()) {
                        // No versions are found which are in the given range. Fallback to local lsp artifacts.
                        val localLspArtifacts = artifactHelper.getAllLocalLspArtifactsWithinManifestRange(DEFAULT_VERSION_RANGE)
                        if (localLspArtifacts.isNotEmpty()) {
                            return@async localLspArtifacts.first().first
                        }
                        throw LspException("Language server versions not found in manifest.", LspException.ErrorCode.NO_COMPATIBLE_LSP_VERSION)
                    }

                    // If there is an LSP Manifest with the same version
                    val target = getTargetFromLspManifest(lspVersions.inRangeVersions)
                    // Get Local LSP files and check if we can re-use existing LSP Artifacts
                    val artifactPath: Path = if (artifactHelper.getExistingLspArtifacts(lspVersions.inRangeVersions, target)) {
                        artifactHelper.getAllLocalLspArtifactsWithinManifestRange(DEFAULT_VERSION_RANGE).first().first
                    } else {
                        artifactHelper.tryDownloadLspArtifacts(project, lspVersions.inRangeVersions, target)
                            ?: throw LspException("Failed to download LSP artifacts", LspException.ErrorCode.DOWNLOAD_FAILED)
                    }
                    artifactHelper.deleteOlderLspArtifacts(DEFAULT_VERSION_RANGE)
                    return@async artifactPath
                }
            }.also {
                artifactDeferred = it
            }
        }.await()
    }

    @VisibleForTesting
    internal fun getLSPVersionsFromManifestWithSpecifiedRange(manifest: ManifestManager.Manifest): LSPVersions {
        if (manifest.versions.isNullOrEmpty()) return LSPVersions(emptyList(), emptyList())

        val (deListed, inRange) = manifest.versions.mapNotNull { version ->
            version.serverVersion?.let { serverVersion ->
                SemVer.parseFromText(serverVersion)?.let { semVer ->
                    when {
                        version.isDelisted != false -> Pair(version, true) // Is deListed
                        semVer in DEFAULT_VERSION_RANGE.let { it.startVersion..it.endVersion } -> Pair(version, false) // Is in range
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
