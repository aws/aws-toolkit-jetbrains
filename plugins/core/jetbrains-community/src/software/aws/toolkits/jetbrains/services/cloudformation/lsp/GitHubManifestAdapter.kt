// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.lsp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class GitHubRelease(
    val tagName: String,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>,
)

data class GitHubAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long,
)

class GitHubManifestAdapter(
    private val environment: CfnLspEnvironment,
) {
    private val httpClient = HttpClient.newBuilder().build()
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    fun getLatestRelease(): GitHubRelease {
        val releases = fetchReleases()
        val filtered = filterByEnvironment(releases)
        return filtered.firstOrNull() ?: throw IllegalStateException("No releases found for $environment")
    }

    private fun fetchReleases(): List<GitHubRelease> {
        val url = "https://api.github.com/repos/${CfnLspServerConfig.GITHUB_OWNER}/${CfnLspServerConfig.GITHUB_REPO}/releases"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("GitHub API error: ${response.statusCode()}")
        }

        return mapper.readValue(response.body())
    }

    private fun filterByEnvironment(releases: List<GitHubRelease>): List<GitHubRelease> {
        return releases
            .filter { release ->
                when (environment) {
                    CfnLspEnvironment.ALPHA -> release.prerelease && release.tagName.endsWith("-alpha")
                    CfnLspEnvironment.BETA -> release.prerelease && release.tagName.endsWith("-beta")
                    CfnLspEnvironment.PROD -> !release.prerelease
                }
            }
            .sortedByDescending { it.tagName }
    }

    fun getAssetForPlatform(release: GitHubRelease): GitHubAsset {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val platform = when {
            osName.contains("mac") || osName.contains("darwin") -> "darwin"
            osName.contains("win") -> "win32"
            osName.contains("linux") -> "linux"
            else -> throw IllegalStateException("Unsupported OS: $osName")
        }

        val arch = when {
            osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
            osArch.contains("amd64") || osArch.contains("x86_64") -> "x64"
            else -> throw IllegalStateException("Unsupported architecture: $osArch")
        }

        val pattern = "$platform-$arch"
        LOG.info("Looking for asset matching: $pattern")

        return release.assets.firstOrNull { it.name.contains(pattern) }
            ?: throw IllegalStateException("No asset found for $platform-$arch")
    }

    companion object {
        private val LOG = logger<GitHubManifestAdapter>()
    }
}
