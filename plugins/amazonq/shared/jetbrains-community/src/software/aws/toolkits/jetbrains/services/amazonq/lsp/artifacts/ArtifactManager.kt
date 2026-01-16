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
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.AwsPlugin
import software.amazon.q.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.telemetry.LanguageServerSetupStage
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
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
        val deListedVersions: List<Version>,
        val inRangeVersions: List<Version>,
    )

    companion object {
        private val DEFAULT_VERSION_RANGE = SupportedManifestVersionRange(
            startVersion = SemVer("1.0.0", 1, 0, 0),
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
                    Telemetry.languageserver.setup.use { all ->
                        all.id("q")
                        all.languageServerSetupStage(LanguageServerSetupStage.All)
                        all.metadata("credentialStartUrl", getStartUrl(project))
                        all.result(MetricResult.Succeeded)

                        try {
                            val lspVersions = Telemetry.languageserver.setup.use { telemetry ->
                                telemetry.id("q")
                                telemetry.languageServerSetupStage(LanguageServerSetupStage.GetManifest)
                                telemetry.metadata("credentialStartUrl", getStartUrl(project))

                                val exception = LspException(
                                    "Language Support is not available, as manifest is missing.",
                                    LspException.ErrorCode.MANIFEST_FETCH_FAILED
                                )
                                telemetry.success(true)
                                val manifest = manifestFetcher.fetch() ?: run {
                                    telemetry.recordException(exception)
                                    telemetry.success(false)
                                    throw exception
                                }

                                getLSPVersionsFromManifestWithSpecifiedRange(manifest)
                            }

                            artifactHelper.removeDelistedVersions(lspVersions.deListedVersions)

                            if (lspVersions.inRangeVersions.isEmpty()) {
                                // No versions are found which are in the given range. Fallback to local lsp artifacts.
                                val localLspArtifacts = artifactHelper.getAllLocalLspArtifactsWithinManifestRange(DEFAULT_VERSION_RANGE)
                                if (localLspArtifacts.isNotEmpty()) {
                                    return@async localLspArtifacts.first().first
                                }
                                throw LspException("Language server versions not found in manifest.", LspException.ErrorCode.NO_COMPATIBLE_LSP_VERSION)
                            }

                            val targetVersion = lspVersions.inRangeVersions.first()

                            // If there is an LSP Manifest with the same version
                            val target = getTargetFromLspManifest(targetVersion)
                            // Get Local LSP files and check if we can re-use existing LSP Artifacts
                            val artifactPath: Path = if (artifactHelper.getExistingLspArtifacts(targetVersion, target)) {
                                artifactHelper.getAllLocalLspArtifactsWithinManifestRange(DEFAULT_VERSION_RANGE).first().first
                            } else {
                                artifactHelper.tryDownloadLspArtifacts(project, targetVersion, target)
                                    ?: throw LspException("Failed to download LSP artifacts", LspException.ErrorCode.DOWNLOAD_FAILED)
                            }

                            artifactHelper.deleteOlderLspArtifacts(DEFAULT_VERSION_RANGE)

                            Telemetry.languageserver.setup.use {
                                it.id("q")
                                it.languageServerSetupStage(LanguageServerSetupStage.Launch)
                                it.metadata("credentialStartUrl", getStartUrl(project))
                                it.setAttribute("isBundledArtifact", false)
                                it.success(true)
                            }
                            return@async artifactPath
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to resolve assets from Flare CDN" }
                            val path = AwsToolkit.PLUGINS_INFO[AwsPlugin.Q]?.path?.resolve("flare") ?: error("not even bundled")
                            logger.info { "Falling back to bundled assets at $path" }

                            all.recordException(e)
                            all.result(MetricResult.Failed)

                            Telemetry.languageserver.setup.use {
                                it.id("q")
                                it.languageServerSetupStage(LanguageServerSetupStage.Launch)
                                it.metadata("credentialStartUrl", getStartUrl(project))
                                it.setAttribute("isBundledArtifact", true)
                                it.success(false)
                            }
                            return@async path
                        }
                    }
                }
            }.also {
                artifactDeferred = it
            }
        }.await()
    }

    @VisibleForTesting
    internal fun getLSPVersionsFromManifestWithSpecifiedRange(manifest: Manifest): LSPVersions {
        if (manifest.versions.isNullOrEmpty()) return LSPVersions(emptyList(), emptyList())

        val (deListed, inRange) = manifest.versions.mapNotNull { version ->
            version.serverVersion?.let { serverVersion ->
                SemVer.parseFromText(serverVersion)?.let { semVer ->
                    when {
                        version.isDelisted != false -> Pair(version, true) // Is deListed
                        (semVer >= DEFAULT_VERSION_RANGE.startVersion && semVer < DEFAULT_VERSION_RANGE.endVersion) -> Pair(version, false) // Is in range
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

    private fun getTargetFromLspManifest(targetVersion: Version): VersionTarget {
        val currentOS = getCurrentOS()
        val currentArchitecture = getCurrentArchitecture()

        val currentTarget = targetVersion.targets?.find { target ->
            target.platform == currentOS && target.arch == currentArchitecture
        }
        if (currentTarget == null) {
            logger.error { "Failed to obtain target for $currentOS and $currentArchitecture" }
            throw LspException("Target not found in the current Version: ${targetVersion.serverVersion}", LspException.ErrorCode.TARGET_NOT_FOUND)
        }
        logger.info { "Target found in the current Version: ${targetVersion.serverVersion}" }
        return currentTarget
    }
}
