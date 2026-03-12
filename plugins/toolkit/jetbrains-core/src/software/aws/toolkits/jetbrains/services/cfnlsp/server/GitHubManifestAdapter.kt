// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.util.SystemInfo
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.getCurrentArchitecture
import software.aws.toolkits.jetbrains.core.lsp.getCurrentOS
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

internal data class ManifestVersion(
    val serverVersion: String,
    val latest: Boolean = false,
    val isDelisted: Boolean = false,
    val targets: List<ManifestTarget>,
)

internal data class ManifestTarget(
    val platform: String,
    val arch: String,
    val nodejs: String? = null,
    val contents: List<ManifestContent>,
)

internal data class ManifestContent(
    val filename: String,
    val url: String,
    val hashes: List<String> = emptyList(),
    val bytes: Long,
)

internal data class ServerRelease(
    val version: String,
    val downloadUrl: String,
    val filename: String,
    val size: Long,
    val hashes: List<String> = emptyList(),
)

internal class GitHubManifestAdapter(
    private val environment: CfnLspEnvironment,
    private val versionRange: SemVerRange = SemVerRange.parse(CfnLspServerConfig.SUPPORTED_VERSION_RANGE),
    private val legacyLinuxDetector: LegacyLinuxDetector = LegacyLinuxDetector(),
) {
    private val httpClient = HttpClient.newBuilder().build()
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Volatile
    private var cachedManifestJson: String? = null

    fun getLatestRelease(): ServerRelease {
        val manifestJson = fetchManifestJson()
        cachedManifestJson = manifestJson
        return parseManifest(manifestJson)
    }

    internal fun parseManifest(json: String): ServerRelease {
        val root = mapper.readTree(json)
        val envKey = environment.name.lowercase()
        var versions: List<ManifestVersion> = mapper.readValue(root.get(envKey).toString())

        if (SystemInfo.isLinux && legacyLinuxDetector.useLegacyLinux()) {
            LOG.info { "Legacy Linux environment detected, remapping to linuxglib2.28" }
            versions = remapLegacyLinux(versions)
        }

        LOG.info {
            "Candidate versions for $environment: ${versions.joinToString { v ->
                "${v.serverVersion}[${v.targets.joinToString(",") { "${it.platform}-${it.arch}" }}]"
            }}"
        }

        val version = latestCompatibleVersion(versions)

        val platform = getEffectivePlatform()
        val arch = getCurrentArchitecture()

        val target = version.targets.firstOrNull { it.platform == platform && it.arch == arch }
            ?: error("No target found for $platform-$arch")

        val content = target.contents.firstOrNull()
            ?: error("No content found for $platform-$arch")

        LOG.info { "Selected ${version.serverVersion} for $platform-$arch" }

        return ServerRelease(
            version = version.serverVersion,
            downloadUrl = content.url,
            filename = content.filename,
            size = content.bytes,
            hashes = content.hashes
        )
    }

    /**
     * Selects the best compatible version from the manifest.
     * Prefers the version marked as "latest" if it's compatible, otherwise falls back to
     * the highest compatible version by semver.
     */
    private fun latestCompatibleVersion(versions: List<ManifestVersion>): ManifestVersion {
        val compatible = versions
            .filter { !it.isDelisted }
            .mapNotNull { v -> SemVer.parse(v.serverVersion)?.let { v to it } }
            .filter { (_, semver) -> versionRange.satisfiedBy(semver) }

        // Prefer the version explicitly marked as latest
        val markedLatest = compatible.firstOrNull { (v, _) -> v.latest }
        if (markedLatest != null) return markedLatest.first

        // Fall back to the highest by semver
        return compatible
            .maxByOrNull { (_, semver) -> semver }
            ?.first
            ?: error("No compatible version found for range ${CfnLspServerConfig.SUPPORTED_VERSION_RANGE} in $environment")
    }

    private fun fetchManifestJson(): String {
        val url = "https://raw.githubusercontent.com/${CfnLspServerConfig.GITHUB_OWNER}/${CfnLspServerConfig.GITHUB_REPO}/main/assets/release-manifest.json"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            error("Manifest fetch error: ${response.statusCode()}")
        }
        return response.body()
    }

    fun getCachedManifest(): String? = cachedManifestJson

    private fun getEffectivePlatform(): String {
        if (SystemInfo.isLinux && legacyLinuxDetector.useLegacyLinux()) {
            return LEGACY_LINUX_PLATFORM
        }
        return getCurrentOS()
    }

    companion object {
        private val LOG = getLogger<GitHubManifestAdapter>()
        private const val LEGACY_LINUX_PLATFORM = "linuxglib2.28"

        internal fun remapLegacyLinux(versions: List<ManifestVersion>): List<ManifestVersion> =
            versions.map { version ->
                val hasLegacy = version.targets.any { it.platform == LEGACY_LINUX_PLATFORM }
                if (!hasLegacy) {
                    LOG.warn { "No legacy Linux build for ${version.serverVersion}" }
                    return@map version
                }
                version.copy(
                    targets = version.targets
                        .filter { it.platform != "linux" }
                        .map { if (it.platform == LEGACY_LINUX_PLATFORM) it.copy(platform = "linux") else it }
                )
            }
    }
}
