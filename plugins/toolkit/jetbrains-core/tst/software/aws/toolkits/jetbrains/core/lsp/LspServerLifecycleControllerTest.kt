// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class LspServerLifecycleControllerTest {
    private val nodeNotFoundMessage = "node not found"

    // A NUL byte can never appear in a filesystem path, so Path.of rejects it with an
    // InvalidPathException on every OS — the same failure class a malformed Windows path setting
    // (e.g. an unquoted drive path with illegal characters) would produce.
    private val malformedConfiguredPath = "\u0000/not/a/real/node"

    @Test
    fun `launchWithRetry succeeds on first try`() {
        var startCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                startCalls++
                "ok"
            },
            invalidateAndReinstall = { },
            restart = { },
        )

        val result = controller.launchWithRetry()
        assertThat(result).isEqualTo("ok")
        assertThat(startCalls).isEqualTo(1)
    }

    @Test
    fun `launchWithRetry retries once on immediate failure`() {
        var startCalls = 0
        var invalidateCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                startCalls++
                if (startCalls == 1) throw RuntimeException("exec failed")
                "ok"
            },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { },
        )

        val result = controller.launchWithRetry()
        assertThat(result).isEqualTo("ok")
        assertThat(startCalls).isEqualTo(2)
        assertThat(invalidateCalls).isEqualTo(1)
    }

    @Test
    fun `launchWithRetry does not retry more than once`() {
        var startCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                startCalls++
                throw RuntimeException("always fails")
            },
            invalidateAndReinstall = { },
            restart = { },
        )

        assertThatThrownBy { controller.launchWithRetry() }
            .isInstanceOf(RuntimeException::class.java)
        assertThat(startCalls).isEqualTo(2) // initial + 1 retry
    }

    @Test
    fun `launchWithRetry resets initialized to false before starting`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
        )

        // First launch succeeds and gets marked as initialized
        controller.launchWithRetry()
        controller.onInitialized()

        // Second launch: launchWithRetry resets initialized, so a pre-init crash
        // would be detectable
        controller.launchWithRetry()
        // After second launch (but before onInitialized), an unexpected stop
        // should be treated as pre-initialization
        controller.onServerStopped(shutdownNormally = false)
        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(1)
    }

    @Test
    fun `onInitialized resets repair budget for next failure`() {
        var startCalls = 0
        var invalidateCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                startCalls++
                if (startCalls == 1 || startCalls == 3) throw RuntimeException("fail")
                "ok"
            },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { },
        )

        // First launch: fails, retries, succeeds
        controller.launchWithRetry()
        assertThat(startCalls).isEqualTo(2)

        // Reset repair budget
        controller.onInitialized()

        // Second launch: fails, retries, succeeds
        controller.launchWithRetry()
        assertThat(startCalls).isEqualTo(4)
        assertThat(invalidateCalls).isEqualTo(2)
    }

    @Test
    fun `onServerStopped normal resets all state`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
        )

        controller.onServerStopped(shutdownNormally = true)
        // Normal shutdown should reset, so next unexpected stop can trigger repair
        controller.onServerStopped(shutdownNormally = false)

        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(1)
    }

    @Test
    fun `onServerStopped normal resets initialized flag`() {
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { },
            restart = { restartCalls++ },
        )

        controller.onInitialized() // marks initialized
        controller.onServerStopped(shutdownNormally = true) // resets initialized
        // Now an unexpected stop should be treated as pre-initialization
        controller.onServerStopped(shutdownNormally = false)
        assertThat(restartCalls).isEqualTo(1)
    }

    @Test
    fun `onServerStopped before initialization restarts without invalidating`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
        )

        controller.onServerStopped(shutdownNormally = false)

        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(1)
    }

    @Test
    fun `onServerStopped before initialization does not loop after repair already attempted`() {
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { },
            restart = { restartCalls++ },
        )

        controller.onServerStopped(shutdownNormally = false) // first: triggers
        controller.onServerStopped(shutdownNormally = false) // second: does not loop

        assertThat(restartCalls).isEqualTo(1)
    }

    @Test
    fun `onServerStopped after initialization does not reinstall`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
        )

        controller.onInitialized()
        controller.onServerStopped(shutdownNormally = false)

        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(0)
    }

    @Test
    fun `full lifecycle - launch, initialize, stop, relaunch detects pre-init crash`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
        )

        // First lifecycle: launch -> initialize -> normal stop
        controller.launchWithRetry()
        controller.onInitialized()
        controller.onServerStopped(shutdownNormally = true)

        // Second lifecycle: launch -> crash before initialize
        controller.launchWithRetry()
        controller.onServerStopped(shutdownNormally = false)

        // Should trigger repair since this is pre-initialization
        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(1)
    }

    @Test
    fun `full lifecycle - launch, crash post-init does not reinstall`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = { "ok" },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
        )

        controller.launchWithRetry()
        controller.onInitialized()
        // Runtime crash (post-init) should NOT trigger reinstall
        controller.onServerStopped(shutdownNormally = false)

        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(0)
    }

    @Test
    fun `launchWithRetry does not repair a failure rejected by the predicate`() {
        var startCalls = 0
        var invalidateCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                startCalls++
                throw LspInstallException("node not found", LspInstallException.ErrorCode.NODE_NOT_FOUND)
            },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { },
            shouldRepair = { it !is LspInstallException },
        )

        assertThatThrownBy { controller.launchWithRetry() }
            .isInstanceOf(LspInstallException::class.java)
        // Rejected failure is rethrown without invalidate/reinstall or a retry.
        assertThat(startCalls).isEqualTo(1)
        assertThat(invalidateCalls).isEqualTo(0)
    }

    @Test
    fun `launchWithRetry repairs a failure accepted by the predicate`() {
        var startCalls = 0
        var invalidateCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                startCalls++
                if (startCalls == 1) throw RuntimeException("exec failed")
                "ok"
            },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { },
            shouldRepair = { it !is LspInstallException },
        )

        val result = controller.launchWithRetry()
        // Ordinary process-start failure is accepted for one repair + retry.
        assertThat(result).isEqualTo("ok")
        assertThat(startCalls).isEqualTo(2)
        assertThat(invalidateCalls).isEqualTo(1)
    }

    @Test
    fun `malformed configured path does not trigger reinstall repair`() {
        var invalidateCalls = 0
        var restartCalls = 0
        val controller = LspServerLifecycleController(
            startProcess = {
                NodeRuntimeResolver.resolve(
                    configuredPath = malformedConfiguredPath,
                    nodeNotFoundMessage = nodeNotFoundMessage,
                    autoDetect = { null },
                    isExecutable = { true },
                )
            },
            invalidateAndReinstall = { invalidateCalls++ },
            restart = { restartCalls++ },
            shouldRepair = { it !is LspInstallException },
        )

        assertThatThrownBy { controller.launchWithRetry() }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.NODE_NOT_FOUND)
            }

        // A malformed setting must not wipe and re-download the language server.
        assertThat(invalidateCalls).isEqualTo(0)
        assertThat(restartCalls).isEqualTo(0)
    }
}
