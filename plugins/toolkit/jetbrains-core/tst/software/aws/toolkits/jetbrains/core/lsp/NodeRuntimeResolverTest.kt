// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.FileSystems
import java.nio.file.Path

class NodeRuntimeResolverTest {
    private val home = Path.of("/mock/home")
    private val noEnv: (String) -> String? = { null }
    private val fs = FileSystems.getDefault()

    @Test
    fun `macOS well-known paths are valid`() {
        val paths = buildWellKnownPaths(Platform.MAC, home)
        assertThat(paths).isNotEmpty
        assertThat(paths.map { it.toString() }).allSatisfy { assertThat(it).doesNotContain("*") }
    }

    @Test
    fun `linux well-known paths are valid`() {
        val paths = buildWellKnownPaths(Platform.LINUX, home)
        assertThat(paths).isNotEmpty
        assertThat(paths.map { it.toString() }).allSatisfy { assertThat(it).doesNotContain("*") }
    }

    @Test
    fun `windows well-known paths are valid`() {
        val paths = buildWellKnownPaths(Platform.WINDOWS, home)
        assertThat(paths).isNotEmpty
        assertThat(paths.map { it.toString() }).allSatisfy { assertThat(it).doesNotContain("*") }
    }

    @Test
    fun `macOS glob patterns are valid PathMatcher globs`() {
        assertValidGlobs(buildGlobPatterns(Platform.MAC, home, noEnv))
    }

    @Test
    fun `linux glob patterns are valid PathMatcher globs`() {
        assertValidGlobs(buildGlobPatterns(Platform.LINUX, home, noEnv))
    }

    @Test
    fun `windows glob patterns are valid PathMatcher globs with env vars`() {
        val env: (String) -> String? = {
            when (it) {
                "APPDATA" -> "C:/Users/test/AppData/Roaming"
                "LOCALAPPDATA" -> "C:/Users/test/AppData/Local"
                else -> null
            }
        }
        assertValidGlobs(buildGlobPatterns(Platform.WINDOWS, home, env))
    }

    @Test
    fun `windows glob patterns handle missing env vars gracefully`() {
        val patterns = buildGlobPatterns(Platform.WINDOWS, home, noEnv)
        // With no env vars, nvm-windows/fnm/volta patterns requiring env vars are skipped
        patterns.forEach { glob ->
            fs.getPathMatcher("glob:$glob")
            Path.of(glob.substringBefore("*"))
        }
    }

    @Test
    fun `nvm glob respects NVM_DIR env var`() {
        val env: (String) -> String? = { if (it == "NVM_DIR") "/custom/nvm" else null }
        val patterns = buildGlobPatterns(Platform.LINUX, home, env)
        assertThat(patterns).anyMatch { "custom" in it && "nvm" in it && "versions" in it }
    }

    @Test
    fun `glob pattern prefixes are valid paths on all platforms`() {
        val windowsEnv: (String) -> String? = {
            when (it) {
                "APPDATA" -> "C:/Users/test/AppData/Roaming"
                "LOCALAPPDATA" -> "C:/Users/test/AppData/Local"
                else -> null
            }
        }

        for (platform in Platform.entries) {
            val env = if (platform == Platform.WINDOWS) windowsEnv else noEnv
            val patterns = buildGlobPatterns(platform, home, env)
            patterns.forEach { glob ->
                Path.of(glob.substringBefore("*"))
            }
        }
    }

    private fun assertValidGlobs(patterns: List<String>) {
        assertThat(patterns).isNotEmpty
        patterns.forEach { glob ->
            assertThat(glob).contains("*")
            fs.getPathMatcher("glob:$glob")
            Path.of(glob.substringBefore("*"))
        }
    }
}
