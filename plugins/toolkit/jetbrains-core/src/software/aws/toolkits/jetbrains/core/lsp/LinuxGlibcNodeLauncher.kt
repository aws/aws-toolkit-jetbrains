// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * A fixed Node.js expression that prints the running interpreter's real ELF path (using
 * `/proc/self/exe` when available so shell wrappers and overridden `argv[0]` are handled), its current
 * `LD_LIBRARY_PATH`, and its runtime glibc version. Each value is emitted on its own prefixed line so it
 * can be parsed even when merged with unrelated diagnostics. `glibcVersionRuntime` is absent on
 * musl/non-glibc runtimes, which safely yields an empty value.
 */
internal const val NODE_RUNTIME_PROBE_EXPR: String =
    "const fs=require('fs');const h=(process.report&&process.report.getReport().header)||{};" +
        "let e=process.execPath;try{e=fs.realpathSync('/proc/self/exe')}catch{};" +
        "process.stdout.write(" +
        "'NODE_EXEC_PATH='+e+'\\n'+" +
        "'NODE_LIBRARY_PATH='+(process.env.LD_LIBRARY_PATH||'')+'\\n'+" +
        "'NODE_GLIBC_RUNTIME='+(h.glibcVersionRuntime||'')+'\\n')"

/** Dynamic-loader file name for a supported CPU architecture. */
internal enum class LinuxArch(val loaderName: String) {
    X64("ld-linux-x86-64.so.2"),
    ARM64("ld-linux-aarch64.so.1"),
}

/** Minimal filesystem surface the launcher needs, extracted so candidate resolution can be tested off-host. */
internal interface RuntimeFileSystem {
    fun realPathOrNull(path: Path): Path?

    fun isRegularFile(path: Path): Boolean

    fun isExecutable(path: Path): Boolean
}

internal object DefaultRuntimeFileSystem : RuntimeFileSystem {
    override fun realPathOrNull(path: Path): Path? = try {
        path.toRealPath()
    } catch (_: Exception) {
        null
    }

    override fun isRegularFile(path: Path): Boolean = Files.isRegularFile(path)

    override fun isExecutable(path: Path): Boolean = Files.isExecutable(path)
}

/**
 * Decides how to launch the CloudFormation language server's Node.js process on Linux so that native
 * modules requiring a newer glibc can load.
 *
 * The launcher first asks the configured Node.js wrapper for its real executable path, current
 * `LD_LIBRARY_PATH`, and runtime glibc version. When that version already meets [required], the
 * wrapper is launched directly, so Windows, macOS, and healthy Linux hosts keep their existing
 * behaviour. If the runtime cannot be identified safely, startup fails with an actionable compatibility
 * error rather than risking a native-module crash.
 *
 * When the runtime glibc is too old, the launcher looks for an alternate glibc runtime explicitly
 * configured by the user or installed by Homebrew/Linuxbrew. It validates each candidate by running
 * the real Node.js ELF through the candidate loader, and returns
 * `<loader> --library-path <paths> <real-node>` for the first candidate that raises the runtime glibc
 * to [required]. If none qualifies it throws a typed [LspInstallException] so startup fails with a
 * clear message instead of crash-looping.
 *
 * Nothing global is mutated: no `LD_LIBRARY_PATH`, `ldconfig`, system files, or downloaded runtime files.
 */
internal class LinuxGlibcNodeLauncher(
    private val isLinux: Boolean = SystemInfo.isLinux,
    private val arch: LinuxArch = currentArch(),
    private val required: GlibcVersion = GlibcVersion.REQUIRED,
    private val env: (String) -> String? = { System.getenv(it) },
    private val runner: BoundedCommandRunner = DefaultBoundedCommandRunner(),
    private val fs: RuntimeFileSystem = DefaultRuntimeFileSystem,
) {
    private data class NodeProbe(val execPath: String, val libraryPath: String, val glibcRuntime: String)

    private data class GlibcCandidate(val loader: Path, val libcDir: Path)

    fun resolveLaunchCommand(nodeWrapper: Path, incompatibleGlibcMessage: String): NodeLaunchCommand {
        val direct = NodeLaunchCommand(listOf(nodeWrapper.toString()))
        if (!isLinux) return direct

        val probe = probeNode(listOf(nodeWrapper.toString(), "-e", NODE_RUNTIME_PROBE_EXPR))
            ?: throw incompatibleGlibc(incompatibleGlibcMessage)

        val runtimeGlibc = GlibcVersion.parse(probe.glibcRuntime)
            ?: throw incompatibleGlibc(incompatibleGlibcMessage)
        if (runtimeGlibc >= required) {
            LOG.debug { "Node.js runtime glibc $runtimeGlibc satisfies the $required floor; launching it directly" }
            return direct
        }

        LOG.info {
            "Node.js runtime glibc $runtimeGlibc is below the $required floor; searching for a compatible glibc runtime"
        }
        val realNode = pathOrNull(probe.execPath) ?: throw incompatibleGlibc(incompatibleGlibcMessage)
        val candidate = discoverLibraryDirectories()
            .firstNotNullOfOrNull { libDir -> validateCandidate(libDir, realNode, probe.libraryPath) }
            ?: throw incompatibleGlibc(incompatibleGlibcMessage)

        val libraryPath = buildLibraryPath(probe.libraryPath, candidate.libcDir)
        LOG.info { "Launching CloudFormation language server through alternate glibc loader ${candidate.loader}" }
        return NodeLaunchCommand(listOf(candidate.loader.toString(), "--library-path", libraryPath, realNode.toString()))
    }

    private fun probeNode(command: List<String>): NodeProbe? {
        val output = runNode(command) ?: return null
        val execPath = output.lineValue("NODE_EXEC_PATH=").orEmpty()
        if (execPath.isBlank()) return null
        return NodeProbe(
            execPath = execPath,
            libraryPath = output.lineValue("NODE_LIBRARY_PATH=").orEmpty(),
            glibcRuntime = output.lineValue("NODE_GLIBC_RUNTIME=").orEmpty(),
        )
    }

    private fun runNode(command: List<String>): String? {
        val output = runner.run(command, PROCESS_TIMEOUT)
        if (!output.succeeded) {
            LOG.debug { "Node.js invocation did not succeed (exit=${output.exitCode}, timedOut=${output.timedOut})" }
            return null
        }
        return output.stdout
    }

    private fun discoverLibraryDirectories(): Sequence<Path> = sequence {
        explicitOptInRoot()?.let { yieldAll(libraryDirectoriesUnder(it)) }
        homebrewGlibcPrefix()?.let { yieldAll(libraryDirectoriesUnder(it)) }
    }.distinct()

    private fun explicitOptInRoot(): Path? = env(ENV_GLIBC_DIR)?.trim()?.takeIf { it.isNotEmpty() }?.let(::pathOrNull)

    private fun homebrewGlibcPrefix(): Path? {
        val output = runner.run(listOf("brew", "--prefix", "glibc"), PROCESS_TIMEOUT)
        if (!output.succeeded) return null
        return output.stdout.lineSequence()
            .map(String::trim)
            .mapNotNull(::pathOrNull)
            .lastOrNull(Path::isAbsolute)
    }

    private fun libraryDirectoriesUnder(root: Path): List<Path> =
        listOf(root, root.resolve("lib"), root.resolve("lib64"))

    private fun validateCandidate(libDir: Path, realNode: Path, nodeLibraryPath: String): GlibcCandidate? {
        val candidate = toCandidate(libDir) ?: return null
        val libraryPath = buildLibraryPath(nodeLibraryPath, candidate.libcDir)
        val command = listOf(candidate.loader.toString(), "--library-path", libraryPath, realNode.toString(), "-e", NODE_RUNTIME_PROBE_EXPR)
        val output = runNode(command) ?: return null
        val glibc = GlibcVersion.parse(output.lineValue("NODE_GLIBC_RUNTIME=")) ?: return null
        return if (glibc >= required) candidate else null
    }

    private fun toCandidate(libDir: Path): GlibcCandidate? {
        val loader = libDir.resolve(arch.loaderName)
        val libc = libDir.resolve(LIBC_FILENAME)
        if (!fs.isRegularFile(loader) || !fs.isExecutable(loader) || !fs.isRegularFile(libc)) return null

        val realLoader = fs.realPathOrNull(loader) ?: return null
        val realLibc = fs.realPathOrNull(libc) ?: return null
        val loaderDir = realLoader.parent ?: return null
        // Loader and libc must resolve into the same real directory; reject symlink escapes / mismatched pairs.
        if (loaderDir != realLibc.parent) return null
        if (!fs.isRegularFile(realLoader) || !fs.isExecutable(realLoader) || !fs.isRegularFile(realLibc)) return null

        return GlibcCandidate(loader = realLoader, libcDir = loaderDir)
    }

    private fun buildLibraryPath(nodeLibraryPath: String, libcDir: Path): String {
        val existing = nodeLibraryPath.split(File.pathSeparatorChar).map { it.trim() }.filter { it.isNotEmpty() }
        return (existing + libcDir.toString()).joinToString(File.pathSeparator)
    }

    private fun incompatibleGlibc(message: String): LspInstallException =
        LspInstallException(message, LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)

    private fun pathOrNull(raw: String): Path? = try {
        Path.of(raw)
    } catch (_: Exception) {
        null
    }

    private fun String.lineValue(prefix: String): String? =
        lineSequence().firstOrNull { it.startsWith(prefix) }?.substring(prefix.length)?.trim()

    companion object {
        private val LOG = getLogger<LinuxGlibcNodeLauncher>()

        /**
         * Public opt-in: point this at a glibc install root (or a lib directory) to supply an alternate
         * runtime on hosts whose system glibc is too old for the CloudFormation language server.
         */
        const val ENV_GLIBC_DIR = "AWS_TOOLKIT_LSP_GLIBC_DIR"

        private const val LIBC_FILENAME = "libc.so.6"
        private val PROCESS_TIMEOUT: Duration = Duration.ofSeconds(10)

        private fun currentArch(): LinuxArch = if (CpuArch.CURRENT == CpuArch.ARM64) LinuxArch.ARM64 else LinuxArch.X64
    }
}
