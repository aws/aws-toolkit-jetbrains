// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

internal enum class Platform { MAC, LINUX, WINDOWS }

private val BIN_DIR = mapOf(Platform.MAC to "bin/", Platform.LINUX to "bin/", Platform.WINDOWS to "")
private val EXE_NAME = mapOf(Platform.MAC to "node", Platform.LINUX to "node", Platform.WINDOWS to "node.exe")

@VisibleForTesting
internal fun buildWellKnownPaths(platform: Platform, home: Path): List<Path> {
    val exeName = EXE_NAME.getValue(platform)
    return buildList {
        if (platform == Platform.MAC) {
            add(Path.of("/opt/homebrew/bin/$exeName"))
            add(Path.of("/usr/local/bin/$exeName"))
            add(home.resolve(".asdf/shims/$exeName"))
        }
        if (platform == Platform.LINUX) {
            add(Path.of("/usr/bin/$exeName"))
            add(Path.of("/usr/local/bin/$exeName"))
            add(Path.of("/snap/bin/$exeName"))
            add(Path.of("/home/linuxbrew/.linuxbrew/bin/$exeName"))
            add(home.resolve(".asdf/shims/$exeName"))
        }
        if (platform == Platform.WINDOWS) {
            add(Path.of("C:/Program Files/nodejs/$exeName"))
            add(Path.of("C:/ProgramData/chocolatey/bin/$exeName"))
            add(home.resolve("scoop/apps/nodejs/current/$exeName"))
        }
    }
}

@VisibleForTesting
internal fun buildGlobPatterns(platform: Platform, home: Path, env: (String) -> String?): List<String> {
    val exeName = EXE_NAME.getValue(platform)
    val bin = BIN_DIR.getValue(platform)

    return buildList {
        if (platform == Platform.MAC) {
            add("/opt/homebrew/Cellar/node*/*/bin/$exeName")
            add("/usr/local/Cellar/node*/*/bin/$exeName")
        }

        // nvm
        val nvmDir = env("NVM_DIR")?.let { Path.of(it) } ?: home.resolve(".nvm")
        if (platform != Platform.WINDOWS) {
            add("$nvmDir/versions/node/v*/bin/$exeName")
        } else {
            val nvmHome = env("NVM_HOME")?.let { Path.of(it) }
                ?: env("APPDATA")?.let { Path.of(it, "nvm") }
            nvmHome?.let { add("$it/v*/$exeName") }
        }

        // fnm
        val fnmBase = when (platform) {
            Platform.MAC -> home.resolve("Library/Application Support/fnm")
            Platform.LINUX -> (env("XDG_DATA_HOME")?.let { Path.of(it) } ?: home.resolve(".local/share")).resolve("fnm")
            Platform.WINDOWS -> env("APPDATA")?.let { Path.of(it, "fnm") }
        }
        fnmBase?.let { add("$it/node-versions/v*/installation/${bin}$exeName") }

        // volta
        val voltaHome = if (platform == Platform.WINDOWS) {
            env("LOCALAPPDATA")?.let { Path.of(it, "Volta") }
        } else {
            home.resolve(".volta")
        }
        voltaHome?.let { add("$it/tools/image/node/*/${bin}$exeName") }
    }
}

/**
 * Resolves a Node.js executable across system PATH, well-known install locations,
 * and version managers (nvm, fnm, volta). GUI-launched IDEs don't inherit shell
 * PATH modifications, so we search common locations directly.
 */
internal object NodeRuntimeResolver {
    private val LOG = getLogger<NodeRuntimeResolver>()
    private val home: Path = Path.of(System.getProperty("user.home"))

    private val platform: Platform = when {
        SystemInfo.isMac -> Platform.MAC
        SystemInfo.isWindows -> Platform.WINDOWS
        else -> Platform.LINUX
    }

    private val exeName = EXE_NAME.getValue(platform)
    private val wellKnownPaths: List<Path> = buildWellKnownPaths(platform, home)
    private val globPatterns: List<String> by lazy { buildGlobPatterns(platform, home) { System.getenv(it) } }

    fun resolve(minVersion: Int = 18): Path? =
        resolveFromPath(minVersion) ?: resolveFromWellKnownLocations(minVersion)

    private fun resolveFromPath(minVersion: Int): Path? =
        PathEnvironmentVariableUtil.findAllExeFilesInPath(exeName)
            .asSequence()
            .map { it.toPath() }
            .filter { Files.isRegularFile(it) && Files.isExecutable(it) }
            .firstNotNullOfOrNull { it.takeIfVersionAtLeast(minVersion) }

    private fun resolveFromWellKnownLocations(minVersion: Int): Path? {
        val fromFixed = wellKnownPaths.asSequence()
            .filter { Files.isRegularFile(it) && Files.isExecutable(it) }

        val fromGlobs = globPatterns.asSequence()
            .flatMap { expandGlob(it) }

        return (fromFixed + fromGlobs)
            .mapNotNull { path ->
                val version = path.nodeVersion()
                if (version != null && version >= minVersion) version to path.toAbsolutePath() else null
            }
            .maxByOrNull { it.first }
            ?.second
    }

    private fun expandGlob(glob: String): Sequence<Path> {
        val parent = Path.of(glob.substringBefore("*")).parent ?: return emptySequence()
        if (!Files.isDirectory(parent)) return emptySequence()

        val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
        val depth = glob.removePrefix(parent.toString()).count { it == '/' || it == '\\' } + 1

        return Files.walk(parent, depth).use { stream ->
            stream
                .filter { matcher.matches(it) && Files.isRegularFile(it) && Files.isExecutable(it) }
                .toList()
        }.asSequence()
    }

    private fun Path.nodeVersion(): Int? = try {
        val output = ExecUtil.execAndGetOutput(GeneralCommandLine(toString(), "--version"), 5000)
        if (output.exitCode == 0) output.stdout.trim().removePrefix("v").split(".")[0].toIntOrNull() else null
    } catch (e: Exception) {
        LOG.debug(e) { "Failed to get version from node at: $this" }
        null
    }

    private fun Path.takeIfVersionAtLeast(minVersion: Int): Path? {
        val version = nodeVersion() ?: return null
        return if (version >= minVersion) {
            LOG.debug { "Node v$version found at: $this" }
            toAbsolutePath()
        } else {
            LOG.debug { "Node v$version < $minVersion at: $this" }
            null
        }
    }
}
