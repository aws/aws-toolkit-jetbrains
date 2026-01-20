// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkits.jetbrains.core.lsp.getCurrentArchitecture
import software.aws.toolkits.jetbrains.core.lsp.getCurrentOS
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

class GitHubManifestAdapter(private val environment: CfnLspEnvironment) {
    private val httpClient = HttpClient.newBuilder().build()
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    fun getLatestRelease(): GitHubRelease {
        val releases = fetchReleases()
        val filtered = filterByEnvironment(releases)
        return filtered.firstOrNull() ?: error("No releases found for $environment")
    }

    private fun fetchReleases(): List<GitHubRelease> {
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

    fun getAssetForPlatform(release: GitHubRelease): GitHubAsset {
        val platform = getCurrentOS()
        val arch = getCurrentArchitecture()
        val pattern = "$platform-$arch"

        LOG.info { "Looking for asset matching: $pattern" }

        return release.assets.firstOrNull { it.name.contains(pattern) }
            ?: error("No asset found for $platform-$arch")
    }

    companion object {
        private val LOG = getLogger<GitHubManifestAdapter>()
    }
}
