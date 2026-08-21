// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.InvalidPathException
import java.nio.file.Path

class NodeRuntimeResolverTest {
    private val home = Path.of("/mock/home")
    private val noEnv: (String) -> String? = { null }
    private val fs = FileSystems.getDefault()

    private val nodeNotFoundMessage = "node not found"
    private val autoDetected = Path.of("/auto/detected/node")

    // A NUL byte can never appear in a filesystem path, so Path.of rejects it with an
    // InvalidPathException on every OS — the same failure class a malformed Windows path setting
    // (e.g. an unquoted drive path with illegal characters) would produce.
    private val malformedConfiguredPath = "\u0000/not/a/real/node"

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

    @Test
    fun `malformed configured path is normalized to NODE_NOT_FOUND with the InvalidPathException as cause`() {
        assertThatThrownBy {
            NodeRuntimeResolver.resolve(
                configuredPath = malformedConfiguredPath,
                nodeNotFoundMessage = nodeNotFoundMessage,
                autoDetect = { error("a malformed configured path must not fall back to auto-detection") },
                isExecutable = { true },
            )
        }.isInstanceOfSatisfying(LspInstallException::class.java) {
            assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.NODE_NOT_FOUND)
            assertThat(it.cause).isInstanceOf(InvalidPathException::class.java)
        }
    }

    @Test
    fun `non-executable configured path falls back to auto-detection`() {
        val resolved = NodeRuntimeResolver.resolve(
            configuredPath = "/configured/node",
            nodeNotFoundMessage = nodeNotFoundMessage,
            autoDetect = { autoDetected },
            isExecutable = { false },
        )

        assertThat(resolved).isEqualTo(autoDetected)
    }

    @Test
    fun `executable configured path is used without auto-detection`() {
        val configured = "/configured/node"

        val resolved = NodeRuntimeResolver.resolve(
            configuredPath = configured,
            nodeNotFoundMessage = nodeNotFoundMessage,
            autoDetect = { error("auto-detection must not run when the configured path is usable") },
            isExecutable = { it == Path.of(configured) },
        )

        assertThat(resolved).isEqualTo(Path.of(configured))
    }

    @Test
    fun `blank configured path uses auto-detection`() {
        val resolved = NodeRuntimeResolver.resolve(
            configuredPath = "   ",
            nodeNotFoundMessage = nodeNotFoundMessage,
            autoDetect = { autoDetected },
            isExecutable = { error("a blank configured path must not be probed for executability") },
        )

        assertThat(resolved).isEqualTo(autoDetected)
    }

    @Test
    fun `minimum version is passed to auto-detection`() {
        var requestedVersion: Int? = null

        val resolved = NodeRuntimeResolver.resolve(
            configuredPath = "",
            nodeNotFoundMessage = nodeNotFoundMessage,
            minVersion = 20,
            autoDetect = {
                requestedVersion = it
                autoDetected
            },
            isExecutable = { true },
        )

        assertThat(resolved).isEqualTo(autoDetected)
        assertThat(requestedVersion).isEqualTo(20)
    }

    @Test
    fun `runtime auto-detection failure is normalized to NODE_NOT_FOUND`() {
        assertThatThrownBy {
            NodeRuntimeResolver.resolve(
                configuredPath = "",
                nodeNotFoundMessage = nodeNotFoundMessage,
                autoDetect = { throw IOException("filesystem walk failed") },
                isExecutable = { true },
            )
        }.isInstanceOfSatisfying(LspInstallException::class.java) {
            assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.NODE_NOT_FOUND)
            assertThat(it.cause).isInstanceOf(IOException::class.java)
        }
    }

    @Test
    fun `no configured path and no auto-detected runtime throws NODE_NOT_FOUND`() {
        assertThatThrownBy {
            NodeRuntimeResolver.resolve(
                configuredPath = "",
                nodeNotFoundMessage = nodeNotFoundMessage,
                autoDetect = { null },
                isExecutable = { true },
            )
        }.isInstanceOfSatisfying(LspInstallException::class.java) {
            assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.NODE_NOT_FOUND)
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
