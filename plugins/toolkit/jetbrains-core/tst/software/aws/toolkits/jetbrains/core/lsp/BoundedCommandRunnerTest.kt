// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.absolutePathString

class BoundedCommandRunnerTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `successful process returns its output and removes the capture file`() {
        lateinit var captureFile: Path
        val runner = DefaultBoundedCommandRunner {
            Files.createTempFile(tempFolder.root.toPath(), "probe-", ".out").also { captureFile = it }
        }

        val output = runner.run(javaCommand("success"), Duration.ofSeconds(10))

        assertThat(output.succeeded).isTrue()
        assertThat(output.stdout).contains("PROCESS_OK")
        assertThat(Files.exists(captureFile)).isFalse()
    }

    @Test
    fun `timed out process is forcibly terminated`() {
        val runner = DefaultBoundedCommandRunner()

        val output = runner.run(javaCommand("sleep"), Duration.ofMillis(200))

        assertThat(output.succeeded).isFalse()
        assertThat(output.timedOut).isTrue()
        assertThat(output.exitCode).isEqualTo(-1)
    }

    @Test
    fun `returned output is capped at 64 KiB`() {
        val runner = DefaultBoundedCommandRunner()

        val output = runner.run(javaCommand("large-output"), Duration.ofSeconds(10))

        assertThat(output.succeeded).isTrue()
        assertThat(output.stdout.toByteArray()).hasSize(64 * 1024)
        assertThat(output.stdout).containsOnlyOnce("OUTPUT_START")
        assertThat(output.stdout).doesNotContain("OUTPUT_END")
    }

    private fun javaCommand(mode: String): List<String> {
        val executable = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java",
        )
        val classpath = Path.of(System.getProperty("user.dir"), "build", "classes", "java", "test")
            .absolutePathString()

        return listOf(
            executable.absolutePathString(),
            "-cp",
            classpath,
            BoundedCommandRunnerTestProcess::class.java.name,
            mode,
        )
    }
}
