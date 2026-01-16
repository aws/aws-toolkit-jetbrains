// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class GitHubManifestAdapterTest {

    @Test
    fun `getAssetForPlatform finds matching asset`() {
        val adapter = GitHubManifestAdapter(CfnLspEnvironment.PROD)
        val release = GitHubRelease(
            tagName = "v1.0.0",
            prerelease = false,
            assets = listOf(
                GitHubAsset("server-darwin-arm64.zip", "https://example.com/darwin-arm64.zip", 1000),
                GitHubAsset("server-darwin-x64.zip", "https://example.com/darwin-x64.zip", 1000),
                GitHubAsset("server-linux-x64.zip", "https://example.com/linux-x64.zip", 1000),
                GitHubAsset("server-windows-x64.zip", "https://example.com/windows-x64.zip", 1000)
            )
        )

        val asset = adapter.getAssetForPlatform(release)

        // Should find an asset matching current platform
        assertThat(asset.name).containsAnyOf("darwin", "linux", "windows")
        assertThat(asset.name).containsAnyOf("arm64", "x64")
    }

    @Test
    fun `getAssetForPlatform throws when no matching asset`() {
        val adapter = GitHubManifestAdapter(CfnLspEnvironment.PROD)
        val release = GitHubRelease(
            tagName = "v1.0.0",
            prerelease = false,
            assets = listOf(
                GitHubAsset("server-unsupported-platform.zip", "https://example.com/unsupported.zip", 1000)
            )
        )

        assertThatThrownBy { adapter.getAssetForPlatform(release) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No asset found")
    }

    @Test
    fun `GitHubRelease data class holds correct values`() {
        val release = GitHubRelease(
            tagName = "v1.2.3",
            prerelease = true,
            assets = emptyList()
        )

        assertThat(release.tagName).isEqualTo("v1.2.3")
        assertThat(release.prerelease).isTrue()
        assertThat(release.assets).isEmpty()
    }

    @Test
    fun `GitHubAsset data class holds correct values`() {
        val asset = GitHubAsset(
            name = "test-asset.zip",
            browserDownloadUrl = "https://example.com/download",
            size = 12345
        )

        assertThat(asset.name).isEqualTo("test-asset.zip")
        assertThat(asset.browserDownloadUrl).isEqualTo("https://example.com/download")
        assertThat(asset.size).isEqualTo(12345)
    }
}
