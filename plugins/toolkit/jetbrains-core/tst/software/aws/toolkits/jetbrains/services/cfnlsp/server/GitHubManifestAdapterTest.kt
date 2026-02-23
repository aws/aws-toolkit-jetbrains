// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun `parseManifest selects highest compatible version by semver`() {
        val adapter = GitHubManifestAdapter(
            environment = CfnLspEnvironment.PROD,
            versionRange = SemVerRange.parse("<2.0.0"),
        )

        // Versions deliberately in wrong order to prove sorting works
        val manifest = buildManifestJson(
            "prod",
            listOf("1.2.0", "1.4.0", "1.0.0", "1.3.1")
        )

        val result = adapter.parseManifest(manifest)
        assertThat(result.version).isEqualTo("1.4.0")
    }

    @Test
    fun `parseManifest respects version range and excludes 2_x`() {
        val adapter = GitHubManifestAdapter(
            environment = CfnLspEnvironment.PROD,
            versionRange = SemVerRange.parse("<2.0.0"),
        )

        val manifest = buildManifestJson(
            "prod",
            listOf("2.1.0", "2.0.0", "1.4.0", "1.2.0")
        )

        val result = adapter.parseManifest(manifest)
        assertThat(result.version).isEqualTo("1.4.0")
    }

    @Test
    fun `parseManifest skips delisted versions`() {
        val adapter = GitHubManifestAdapter(
            environment = CfnLspEnvironment.PROD,
            versionRange = SemVerRange.parse("<2.0.0"),
        )

        val manifest = buildManifestJsonWithDelisted(
            "prod",
            listOf("1.4.0" to true, "1.3.0" to false, "1.2.0" to false)
        )

        val result = adapter.parseManifest(manifest)
        assertThat(result.version).isEqualTo("1.3.0")
    }

    @Test
    fun `parseManifest errors when no compatible version exists`() {
        val adapter = GitHubManifestAdapter(
            environment = CfnLspEnvironment.PROD,
            versionRange = SemVerRange.parse("<1.0.0"),
        )

        val manifest = buildManifestJson("prod", listOf("1.4.0", "1.2.0"))

        assertThatThrownBy { adapter.parseManifest(manifest) }
            .hasMessageContaining("No compatible version found")
    }

    @Test
    fun `parseManifest handles beta versions correctly`() {
        val adapter = GitHubManifestAdapter(
            environment = CfnLspEnvironment.BETA,
            versionRange = SemVerRange.parse("<2.0.0"),
        )

        val manifest = buildManifestJson(
            "beta",
            listOf("1.4.0-beta", "1.2.0-beta")
        )

        val result = adapter.parseManifest(manifest)
        assertThat(result.version).isEqualTo("1.4.0-beta")
    }

    @Test
    fun `parseManifest numeric sort not lexicographic`() {
        val adapter = GitHubManifestAdapter(
            environment = CfnLspEnvironment.PROD,
            versionRange = SemVerRange.parse("<100.0.0"),
        )

        // Lexicographic: "9.0.0" > "10.0.0" — semver: 10.0.0 > 9.0.0
        val manifest = buildManifestJson("prod", listOf("9.0.0", "10.0.0", "2.0.0"))

        val result = adapter.parseManifest(manifest)
        assertThat(result.version).isEqualTo("10.0.0")
    }

    // --- helpers ---

    private fun buildManifestJson(env: String, versions: List<String>): String {
        val mapper = jacksonObjectMapper()
        val versionObjects = versions.map { v ->
            mapOf(
                "server_version" to v,
                "is_delisted" to false,
                "targets" to listOf(currentPlatformTarget(v))
            )
        }
        return mapper.writeValueAsString(mapOf(env to versionObjects))
    }

    private fun buildManifestJsonWithDelisted(env: String, versions: List<Pair<String, Boolean>>): String {
        val mapper = jacksonObjectMapper()
        val versionObjects = versions.map { (v, delisted) ->
            mapOf(
                "server_version" to v,
                "is_delisted" to delisted,
                "targets" to listOf(currentPlatformTarget(v))
            )
        }
        return mapper.writeValueAsString(mapOf(env to versionObjects))
    }

    private fun currentPlatformTarget(version: String): Map<String, Any> {
        val os = System.getProperty("os.name").lowercase().let {
            when {
                it.contains("mac") -> "darwin"
                it.contains("win") -> "windows"
                else -> "linux"
            }
        }
        val arch = System.getProperty("os.arch").lowercase().let {
            when {
                it.contains("aarch64") || it.contains("arm64") -> "arm64"
                else -> "x64"
            }
        }
        return mapOf(
            "platform" to os,
            "arch" to arch,
            "contents" to listOf(
                mapOf(
                    "filename" to "server-$version.zip",
                    "url" to "https://example.com/server-$version.zip",
                    "hashes" to emptyList<String>(),
                    "bytes" to 50000000
                )
            )
        )
    }
}
