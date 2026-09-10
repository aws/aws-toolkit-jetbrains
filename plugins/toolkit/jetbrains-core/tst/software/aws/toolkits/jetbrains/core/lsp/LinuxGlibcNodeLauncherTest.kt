// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Path

class LinuxGlibcNodeLauncherTest {
    private val wrapper = Path.of("/usr/bin/node")
    private val realNode = "/opt/node/bin/node"
    private val nodeLib = "/opt/node/lib"
    private val message = "CloudFormation Language Server needs a newer glibc"

    private fun ok(stdout: String) = CommandOutput(exitCode = 0, stdout = stdout, timedOut = false)

    private fun failure() = CommandOutput(exitCode = 127, stdout = "", timedOut = false)

    private fun report(execPath: String = realNode, libraryPath: String = nodeLib, glibc: String): String =
        "NODE_EXEC_PATH=$execPath\nNODE_LIBRARY_PATH=$libraryPath\nNODE_GLIBC_RUNTIME=$glibc\n"

    /**
     * Answers the node probe, an optional `brew --prefix glibc`, and every loader validation. A command
     * containing `--library-path` is a validation run; a command whose second token is `-e` is the wrapper probe.
     */
    private fun scenarioRunner(
        probe: CommandOutput,
        brewPrefix: String? = null,
        validationGlibc: String = "2.28",
    ): BoundedCommandRunner = BoundedCommandRunner { command, _ ->
        when {
            command.firstOrNull() == "brew" -> if (brewPrefix != null) ok(brewPrefix) else failure()
            command.contains("--library-path") -> ok(report(glibc = validationGlibc))
            command.getOrNull(1) == "-e" -> probe
            else -> failure()
        }
    }

    private class FakeFs(
        private val regularFiles: Set<String> = emptySet(),
        private val executables: Set<String> = emptySet(),
        private val realPaths: Map<String, String> = emptyMap(),
    ) : RuntimeFileSystem {
        override fun realPathOrNull(path: Path): Path? = Path.of(realPaths[path.toString()] ?: path.toString())

        override fun isRegularFile(path: Path): Boolean = path.toString() in regularFiles

        override fun isExecutable(path: Path): Boolean = path.toString() in executables
    }

    private fun fsWithCandidate(libDir: String, loaderName: String): FakeFs {
        val loader = "$libDir/$loaderName"
        val libc = "$libDir/libc.so.6"
        return FakeFs(
            regularFiles = setOf(loader, libc),
            executables = setOf(loader),
        )
    }

    @Test
    fun `non-linux never probes and launches the wrapper directly`() {
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = false,
            arch = LinuxArch.X64,
            env = { error("environment must not be read on non-linux") },
            runner = BoundedCommandRunner { _, _ -> error("no process may be launched on non-linux") },
            fs = FakeFs(),
        )

        assertThat(launcher.resolveLaunchCommand(wrapper, message).command).containsExactly(wrapper.toString())
    }

    @Test
    fun `runtime glibc at or above the floor launches the wrapper directly`() {
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.31"))),
            fs = FakeFs(),
        )

        assertThat(launcher.resolveLaunchCommand(wrapper, message).command).containsExactly(wrapper.toString())
    }

    @Test
    fun `probes the wrapper with a fixed expression passed as an argument vector`() {
        var probeCommand: List<String>? = null
        val runner = BoundedCommandRunner { command, _ ->
            if (command.getOrNull(1) == "-e") probeCommand = command
            ok(report(glibc = "2.31"))
        }
        val launcher = LinuxGlibcNodeLauncher(isLinux = true, arch = LinuxArch.X64, env = {
            null
        }, runner = runner, fs = FakeFs())

        launcher.resolveLaunchCommand(wrapper, message)

        assertThat(probeCommand).containsExactly(wrapper.toString(), "-e", NODE_RUNTIME_PROBE_EXPR)
        assertThat(NODE_RUNTIME_PROBE_EXPR).contains("/proc/self/exe")
    }

    @Test
    fun `musl or unknown libc fails with a typed compatibility error`() {
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { null },
            runner = scenarioRunner(probe = ok(report(glibc = ""))),
            fs = FakeFs(),
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)
            }
    }

    @Test
    fun `malformed probe output fails with a typed compatibility error`() {
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { null },
            runner = scenarioRunner(probe = ok("this is not the output we expect")),
            fs = FakeFs(),
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)
            }
    }

    @Test
    fun `timed out probe fails with a typed compatibility error`() {
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { null },
            runner = BoundedCommandRunner { _, _ ->
                CommandOutput(exitCode = -1, stdout = report(glibc = "2.26"), timedOut = true)
            },
            fs = FakeFs(),
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)
            }
    }

    @Test
    fun `old glibc with a valid explicit candidate launches through the loader against the real node`() {
        val root = "/custom/glibc228"
        val libDir = "$root/lib"
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.26"))),
            fs = fsWithCandidate(libDir, "ld-linux-x86-64.so.2"),
        )

        val command = launcher.resolveLaunchCommand(wrapper, message).command

        assertThat(command).containsExactly("$libDir/ld-linux-x86-64.so.2", "--library-path", "$nodeLib:$libDir", realNode)
    }

    @Test
    fun `library path lists the node paths before the candidate libc directory`() {
        val root = "/custom/glibc228"
        val libDir = "$root/lib"
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            runner = scenarioRunner(probe = ok(report(libraryPath = "/node/a:/node/b", glibc = "2.26"))),
            fs = fsWithCandidate(libDir, "ld-linux-x86-64.so.2"),
        )

        val command = launcher.resolveLaunchCommand(wrapper, message).command

        val libraryPath = command[command.indexOf("--library-path") + 1]
        assertThat(libraryPath).isEqualTo("/node/a:/node/b:$libDir")
    }

    @Test
    fun `arm64 loader name is used on arm64`() {
        val root = "/custom/glibc228"
        val libDir = "$root/lib"
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.ARM64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.26"))),
            fs = fsWithCandidate(libDir, "ld-linux-aarch64.so.1"),
        )

        assertThat(launcher.resolveLaunchCommand(wrapper, message).command.first()).isEqualTo("$libDir/ld-linux-aarch64.so.1")
    }

    @Test
    fun `explicit opt-in environment variable is used before Homebrew discovery`() {
        val root = "/custom/glibc"
        val libDir = "$root/lib"
        var brewCalls = 0
        val runner = BoundedCommandRunner { command, _ ->
            when {
                command.firstOrNull() == "brew" -> {
                    brewCalls++
                    failure()
                }

                command.contains("--library-path") -> ok(report(glibc = "2.28"))

                else -> ok(report(glibc = "2.26"))
            }
        }
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            runner = runner,
            fs = fsWithCandidate(libDir, "ld-linux-x86-64.so.2"),
        )

        assertThat(launcher.resolveLaunchCommand(wrapper, message).command.first()).isEqualTo("$libDir/ld-linux-x86-64.so.2")
        assertThat(brewCalls).isZero()
    }

    @Test
    fun `homebrew glibc prefix is used to discover a candidate`() {
        val prefix = "/home/linuxbrew/.linuxbrew/opt/glibc"
        val libDir = "$prefix/lib"
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { null },
            runner = scenarioRunner(
                probe = ok(report(glibc = "2.26")),
                brewPrefix = "Warning: using a custom installation\n$prefix\n",
            ),
            fs = fsWithCandidate(libDir, "ld-linux-x86-64.so.2"),
        )

        assertThat(launcher.resolveLaunchCommand(wrapper, message).command.first()).isEqualTo("$libDir/ld-linux-x86-64.so.2")
    }

    @Test
    fun `a symlink escape candidate is rejected and a later valid candidate is used`() {
        val badRoot = "/custom/bad"
        val badLibDir = "$badRoot/lib"
        val goodRoot = "/home/linuxbrew/.linuxbrew/opt/glibc"
        val goodLibDir = "$goodRoot/lib"
        val fs = FakeFs(
            regularFiles = setOf(
                "$badLibDir/ld-linux-x86-64.so.2",
                "$badLibDir/libc.so.6",
                "$goodLibDir/ld-linux-x86-64.so.2",
                "$goodLibDir/libc.so.6",
            ),
            executables = setOf("$badLibDir/ld-linux-x86-64.so.2", "$goodLibDir/ld-linux-x86-64.so.2"),
            // The bad loader resolves outside its lib directory, so its real parent no longer matches libc's.
            realPaths = mapOf("$badLibDir/ld-linux-x86-64.so.2" to "/somewhere/else/ld-linux-x86-64.so.2"),
        )
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) badRoot else null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.26")), brewPrefix = goodRoot),
            fs = fs,
        )

        assertThat(launcher.resolveLaunchCommand(wrapper, message).command.first()).isEqualTo("$goodLibDir/ld-linux-x86-64.so.2")
    }

    @Test
    fun `a candidate missing its libc is rejected`() {
        val root = "/custom/glibc228"
        val libDir = "$root/lib"
        val fs = FakeFs(
            regularFiles = setOf("$libDir/ld-linux-x86-64.so.2"),
            executables = setOf("$libDir/ld-linux-x86-64.so.2"),
        )
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.26"))),
            fs = fs,
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)
            }
    }

    @Test
    fun `a candidate with a non-executable loader is rejected`() {
        val root = "/custom/glibc228"
        val libDir = "$root/lib"
        val fs = FakeFs(
            regularFiles = setOf("$libDir/ld-linux-x86-64.so.2", "$libDir/libc.so.6"),
            executables = emptySet(),
        )
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.26"))),
            fs = fs,
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOf(LspInstallException::class.java)
    }

    @Test
    fun `a candidate whose loader does not raise glibc to the floor is rejected`() {
        val root = "/custom/glibc228"
        val libDir = "$root/lib"
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { if (it == LinuxGlibcNodeLauncher.ENV_GLIBC_DIR) root else null },
            // Structurally valid, but running node through it still reports an old glibc.
            runner = scenarioRunner(probe = ok(report(glibc = "2.26")), validationGlibc = "2.26"),
            fs = fsWithCandidate(libDir, "ld-linux-x86-64.so.2"),
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)
            }
    }

    @Test
    fun `old glibc with no discoverable candidate fails with a typed incompatible glibc exception`() {
        val launcher = LinuxGlibcNodeLauncher(
            isLinux = true,
            arch = LinuxArch.X64,
            env = { null },
            runner = scenarioRunner(probe = ok(report(glibc = "2.26"))),
            fs = FakeFs(),
        )

        assertThatThrownBy { launcher.resolveLaunchCommand(wrapper, message) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.INCOMPATIBLE_GLIBC)
                assertThat(it.message).isEqualTo(message)
            }
    }
}
