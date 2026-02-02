// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.util.SystemInfo
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.getCurrentArchitecture
import software.aws.toolkits.jetbrains.core.lsp.getCurrentOS
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Release manifest types (primary source)
internal data class ManifestVersion(
    val serverVersion: String,
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

// GitHub releases types (fallback)
internal data class GitHubRelease(
    val tagName: String,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>,
)

internal data class GitHubAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long,
)

// Unified result type
internal data class ServerRelease(
    val version: String,
    val downloadUrl: String,
    val filename: String,
    val size: Long,
    val hashes: List<String> = emptyList(),
)

internal class GitHubManifestAdapter(
    private val environment: CfnLspEnvironment,
    private val legacyLinuxDetector: LegacyLinuxDetector = LegacyLinuxDetector(),
) {
    private val httpClient = HttpClient.newBuilder().build()
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    // Cached manifest JSON for offline fallback
    @Volatile
    private var cachedManifestJson: String? = null

    fun getLatestRelease(): ServerRelease = try {
        getFromManifest()
    } catch (e: Exception) {
        LOG.warn(e) { "Failed to fetch release manifest, falling back to GitHub Releases API" }
        getFromGitHubReleases()
    }

    private fun getFromManifest(): ServerRelease {
        val manifestJson = fetchManifestJson()
        cachedManifestJson = manifestJson

        return parseManifest(manifestJson)
    }

    internal fun parseManifest(json: String): ServerRelease {
        val root = mapper.readTree(json)
        val envKey = environment.name.lowercase()
        var versions: List<ManifestVersion> = mapper.readValue(root.get(envKey).toString())

        // Apply legacy Linux remapping if needed
        if (SystemInfo.isLinux && legacyLinuxDetector.useLegacyLinux()) {
            LOG.info { "Legacy Linux environment detected, remapping to linuxglib2.28" }
            versions = remapLegacyLinux(versions)
        }

        LOG.info {
            "Candidate versions for $environment: ${versions.map { v ->
                "${v.serverVersion}[${v.targets.joinToString(
                    ","
                ) { "${it.platform}-${it.arch}" }}]"
            }}"
        }

        val version = versions.firstOrNull { !it.isDelisted }
            ?: error("No versions found for $environment")

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

    private fun getFromGitHubReleases(): ServerRelease {
        val releases = fetchGitHubReleases()
        val filtered = filterByEnvironment(releases)
        val release = filtered.firstOrNull() ?: error("No releases found for $environment")
        val asset = getAssetForPlatform(release)

        LOG.info { "Found version ${release.tagName} from GitHub Releases" }

        return ServerRelease(
            version = release.tagName,
            downloadUrl = asset.browserDownloadUrl,
            filename = asset.name,
            size = asset.size
        )
    }

    private fun fetchGitHubReleases(): List<GitHubRelease> {
        val url = "https://api.github.com/repos/${CfnLspServerConfig.GITHUB_OWNER}/${CfnLspServerConfig.GITHUB_REPO}/releases"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            error("GitHub API error: ${response.statusCode()}")
        }

        return mapper.readValue(response.body())
    }

    private fun filterByEnvironment(releases: List<GitHubRelease>): List<GitHubRelease> =
        releases
            .filter { release ->
                when (environment) {
                    CfnLspEnvironment.ALPHA -> release.prerelease && release.tagName.endsWith("-alpha")
                    CfnLspEnvironment.BETA -> release.prerelease && release.tagName.endsWith("-beta")
                    CfnLspEnvironment.PROD -> !release.prerelease
                }
            }
            .sortedByDescending { it.tagName }

    private fun getAssetForPlatform(release: GitHubRelease): GitHubAsset {
        val platform = getEffectivePlatform()
        val arch = getCurrentArchitecture()
        // Asset filenames use "win32" not "windows"
        val filenamePlatform = if (platform == "windows") "win32" else platform

        return release.assets.firstOrNull { asset ->
            asset.name.contains("$filenamePlatform-$arch")
        } ?: error("No asset found for $platform-$arch")
    }

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
