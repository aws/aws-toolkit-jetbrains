// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class DefaultManifestAdapterTest {
    private val mapper = jacksonObjectMapper()
    private val adapter = DefaultManifestAdapter()

    @Test
    fun `parseManifest parses top-level versions`() {
        val json = mapper.writeValueAsString(
            mapOf("versions" to listOf(versionEntry("1.2.0"), versionEntry("1.4.0")))
        )

        val result = adapter.parseManifest(json)

        assertThat(result.versions.map { it.serverVersion }).containsExactly("1.2.0", "1.4.0")
    }

    @Test
    fun `parseManifest allows an empty versions array`() {
        val result = adapter.parseManifest(mapper.writeValueAsString(mapOf("versions" to emptyList<Any>())))

        assertThat(result.versions).isEmpty()
    }

    @Test
    fun `parseManifest does not infer environment channels`() {
        val json = mapper.writeValueAsString(
            mapOf(
                "alpha" to listOf(versionEntry("1.0.0-alpha")),
                "beta" to listOf(versionEntry("1.0.0-beta")),
                "prod" to listOf(versionEntry("1.0.0")),
            )
        )

        assertThatThrownBy { adapter.parseManifest(json) }
            .hasMessageContaining("top-level 'versions' array")
    }

    @Test
    fun `parseManifest errors when versions is missing or not an array`() {
        assertThatThrownBy { adapter.parseManifest("{}") }
            .hasMessageContaining("top-level 'versions' array")
        assertThatThrownBy { adapter.parseManifest("{\"versions\":{}}") }
            .hasMessageContaining("top-level 'versions' array")
    }

    @Test
    fun `parseManifest preserves version flags`() {
        val json = mapper.writeValueAsString(
            mapOf(
                "versions" to listOf(
                    versionEntry("1.4.0", latest = true, delisted = false),
                    versionEntry("1.3.0", latest = false, delisted = true),
                )
            )
        )

        val result = adapter.parseManifest(json)

        assertThat(result.versions[0].latest).isTrue()
        assertThat(result.versions[1].isDelisted).isTrue()
    }

    @Test
    fun `parseManifest preserves targets contents and hashes`() {
        val entry = mapOf(
            "serverVersion" to "1.3.0",
            "latest" to false,
            "isDelisted" to false,
            "targets" to listOf(
                target("darwin", "arm64", content("server.zip", listOf("sha256:abcdef"))),
                target("win32", "x64", content("server.exe", emptyList())),
            )
        )
        val json = mapper.writeValueAsString(mapOf("versions" to listOf(entry)))

        val result = adapter.parseManifest(json)

        assertThat(result.versions[0].targets.map { it.platform }).containsExactly("darwin", "win32")
        assertThat(result.versions[0].targets[0].contents[0].filename).isEqualTo("server.zip")
        assertThat(result.versions[0].targets[0].contents[0].hashes).containsExactly("sha256:abcdef")
    }

    @Test
    fun `parseManifest preserves linux legacy platform literal`() {
        val entry = mapOf(
            "serverVersion" to "1.3.0",
            "latest" to false,
            "isDelisted" to false,
            "targets" to listOf(
                target("linux", "x64", content("linux.zip", emptyList())),
                target("linuxglib2.28", "x64", content("legacy.zip", emptyList())),
            )
        )
        val result = adapter.parseManifest(mapper.writeValueAsString(mapOf("versions" to listOf(entry))))

        assertThat(result.versions[0].targets.map { it.platform }).containsExactly("linux", "linuxglib2.28")
    }

    private fun versionEntry(version: String, latest: Boolean = false, delisted: Boolean = false): Map<String, Any> =
        mapOf(
            "serverVersion" to version,
            "latest" to latest,
            "isDelisted" to delisted,
            "targets" to listOf(target("darwin", "arm64", content("server-$version.zip", emptyList())))
        )

    private fun target(platform: String, arch: String, vararg contents: Map<String, Any>): Map<String, Any> = mapOf(
        "platform" to platform,
        "arch" to arch,
        "contents" to contents.toList()
    )

    private fun content(filename: String, hashes: List<String>): Map<String, Any> = mapOf(
        "filename" to filename,
        "url" to "https://example.com/$filename",
        "hashes" to hashes,
        "bytes" to 50_000_000
    )
}
