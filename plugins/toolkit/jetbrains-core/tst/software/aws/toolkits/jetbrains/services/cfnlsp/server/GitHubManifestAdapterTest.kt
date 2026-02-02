// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GitHubManifestAdapterTest {

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

    @Test
    fun `ManifestVersion data class holds correct values`() {
        val version = ManifestVersion(
            serverVersion = "1.3.0",
            isDelisted = false,
            targets = emptyList()
        )

        assertThat(version.serverVersion).isEqualTo("1.3.0")
        assertThat(version.isDelisted).isFalse()
        assertThat(version.targets).isEmpty()
    }

    @Test
    fun `ManifestTarget data class holds correct values`() {
        val target = ManifestTarget(
            platform = "darwin",
            arch = "arm64",
            nodejs = "22",
            contents = listOf(
                ManifestContent(
                    filename = "server.zip",
                    url = "https://example.com/server.zip",
                    bytes = 50000000
                )
            )
        )

        assertThat(target.platform).isEqualTo("darwin")
        assertThat(target.arch).isEqualTo("arm64")
        assertThat(target.nodejs).isEqualTo("22")
        assertThat(target.contents).hasSize(1)
        assertThat(target.contents[0].filename).isEqualTo("server.zip")
    }

    @Test
    fun `ServerRelease data class holds correct values`() {
        val release = ServerRelease(
            version = "1.3.0",
            downloadUrl = "https://example.com/download.zip",
            filename = "server.zip",
            size = 50000000,
            hashes = listOf("sha256:abc123")
        )

        assertThat(release.version).isEqualTo("1.3.0")
        assertThat(release.downloadUrl).isEqualTo("https://example.com/download.zip")
        assertThat(release.filename).isEqualTo("server.zip")
        assertThat(release.size).isEqualTo(50000000)
        assertThat(release.hashes).containsExactly("sha256:abc123")
    }

    @Test
    fun `remapLegacyLinux replaces linux with linuxglib2_28`() {
        val versions = listOf(
            ManifestVersion(
                serverVersion = "1.0.0",
                targets = listOf(
                    ManifestTarget("linux", "x64", "22", listOf(ManifestContent("a.zip", "url", emptyList(), 100))),
                    ManifestTarget("linuxglib2.28", "x64", "18", listOf(ManifestContent("b.zip", "url", emptyList(), 100))),
                    ManifestTarget("darwin", "x64", "22", listOf(ManifestContent("c.zip", "url", emptyList(), 100))),
                )
            )
        )

        val remapped = GitHubManifestAdapter.remapLegacyLinux(versions)

        assertThat(remapped[0].targets.map { it.platform }).containsExactlyInAnyOrder("linux", "darwin")
        assertThat(remapped[0].targets.first { it.platform == "linux" }.nodejs).isEqualTo("18")
    }

    @Test
    fun `remapLegacyLinux preserves version without legacy target`() {
        val versions = listOf(
            ManifestVersion(
                serverVersion = "1.0.0",
                targets = listOf(
                    ManifestTarget("linux", "x64", "22", listOf(ManifestContent("a.zip", "url", emptyList(), 100))),
                    ManifestTarget("darwin", "x64", "22", listOf(ManifestContent("c.zip", "url", emptyList(), 100))),
                )
            )
        )

        val remapped = GitHubManifestAdapter.remapLegacyLinux(versions)

        assertThat(remapped[0].targets.map { it.platform }).containsExactlyInAnyOrder("linux", "darwin")
    }
}
