// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.InitializeResult
import org.jetbrains.annotations.Nls
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import java.util.concurrent.atomic.AtomicBoolean

abstract class LspProcessLauncher(
    project: Project,
    presentableName: @Nls String,
    invalidateAndReinstall: () -> Unit = {},
    restart: () -> Unit = {},
    shouldRepair: (Throwable) -> Boolean = { true },
) : ProjectWideLspServerDescriptor(project, presentableName) {

    private val lifecycle = LspServerLifecycleController(
        startProcess = ::createServerProcess,
        invalidateAndReinstall = invalidateAndReinstall,
        restart = restart,
        shouldRepair = shouldRepair,
    )

    final override fun startServerProcess(): OSProcessHandler = lifecycle.launchWithRetry()

    final override val lspServerListener: LspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
            lifecycle.onInitialized()
        }

        override fun serverStopped(shutdownNormally: Boolean) {
            lifecycle.onServerStopped(shutdownNormally)
        }
    }

    private fun createServerProcess(): OSProcessHandler = super.startServerProcess()
}

internal class LspServerLifecycleController<T>(
    private val startProcess: () -> T,
    private val invalidateAndReinstall: () -> Unit = {},
    private val restart: () -> Unit = {},
    private val shouldRepair: (Throwable) -> Boolean = { true },
) {
    private val repairAttempted = AtomicBoolean(false)

    private val initialized = AtomicBoolean(false)

    fun launchWithRetry(): T {
        initialized.set(false)
        return try {
            startProcess()
        } catch (e: Exception) {
            if (!shouldRepair(e)) {
                LOG.info { "Process creation failure not eligible for repair, rethrowing: ${e.message}" }
                throw e
            }
            if (!repairAttempted.compareAndSet(false, true)) {
                LOG.warn(e) { "Process creation failed after repair, not retrying again" }
                throw e
            }
            LOG.info { "Process creation failed, attempting reinstall and retry" }
            invalidateAndReinstall()
            startProcess()
        }
    }

    fun onInitialized() {
        repairAttempted.set(false)
        initialized.set(true)
    }

    fun onServerStopped(shutdownNormally: Boolean) {
        if (shutdownNormally) {
            // Normal stop: reset all state for next startup
            repairAttempted.set(false)
            initialized.set(false)
            return
        }

        if (initialized.get()) {
            // Post-initialization crash: not an installation problem
            LOG.info { "Server stopped after initialization — not an installation problem" }
            return
        }

        // Pre-initialization crash: attempt one repair
        if (!repairAttempted.compareAndSet(false, true)) {
            LOG.warn { "Pre-initialize stop after repair already attempted, not retrying" }
            return
        }
        LOG.info { "Pre-initialize unexpected stop, requesting restart" }
        restart()
    }

    companion object {
        private val LOG = getLogger<LspServerLifecycleController<*>>()
    }
}
