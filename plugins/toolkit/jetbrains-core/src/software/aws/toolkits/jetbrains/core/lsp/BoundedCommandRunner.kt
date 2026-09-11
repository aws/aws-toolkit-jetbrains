// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Result of a bounded command execution. [stdout] is capped in size, and process failures are reported
 * here rather than thrown.
 */
internal data class CommandOutput(val exitCode: Int, val stdout: String, val timedOut: Boolean) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

/**
 * Runs a command as an explicit argument vector (never through a shell), captures a bounded amount of
 * merged stdout/stderr, and forcibly destroys the process if it does not finish within the timeout.
 * Implementations must not throw for ordinary process failures.
 */
internal fun interface BoundedCommandRunner {
    fun run(command: List<String>, timeout: Duration): CommandOutput
}

internal class DefaultBoundedCommandRunner(
    private val createOutputFile: () -> Path = { Files.createTempFile("aws-toolkit-lsp-probe-", ".out") },
) : BoundedCommandRunner {
    private val log = getLogger<DefaultBoundedCommandRunner>()

    override fun run(command: List<String>, timeout: Duration): CommandOutput {
        val outputFile = try {
            createOutputFile()
        } catch (e: Exception) {
            log.warn(e) { "Could not create a temp file to capture command output" }
            return CommandOutput(exitCode = -1, stdout = "", timedOut = false)
        }

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start()
            process.outputStream.close()

            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                CommandOutput(exitCode = process.exitValue(), stdout = readBounded(outputFile), timedOut = false)
            } else {
                process.destroyForcibly()
                process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)
                CommandOutput(exitCode = -1, stdout = readBounded(outputFile), timedOut = true)
            }
        } catch (e: Exception) {
            log.debug(e) { "Command could not be executed: ${command.firstOrNull()}" }
            CommandOutput(exitCode = -1, stdout = "", timedOut = false)
        } finally {
            runCatching { Files.deleteIfExists(outputFile) }
        }
    }

    private fun readBounded(file: Path): String = try {
        file.toFile().inputStream().use { stream ->
            val buffer = ByteArray(MAX_OUTPUT_BYTES)
            var total = 0
            while (total < MAX_OUTPUT_BYTES) {
                val read = stream.read(buffer, total, MAX_OUTPUT_BYTES - total)
                if (read < 0) break
                total += read
            }
            String(buffer, 0, total, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        log.debug(e) { "Could not read captured command output" }
        ""
    }

    private companion object {
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val TERMINATION_GRACE_SECONDS = 1L
    }
}
