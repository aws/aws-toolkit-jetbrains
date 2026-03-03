// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.utils.notifyError
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal sealed class PollResult<out T> {
    data class Success<T>(val value: T) : PollResult<T>()
    data class Failed(val reason: String?) : PollResult<Nothing>()
}

internal abstract class PollingWorkflow(
    protected val project: Project,
) {
    protected abstract fun fetchStatus(id: String): CompletableFuture<GetStackActionStatusResult?>
    protected abstract fun handleTerminalState(status: GetStackActionStatusResult, id: String): CompletableFuture<PollResult<*>?>
    protected abstract val operationTitle: String

    fun <T> poll(id: String): CompletableFuture<PollResult<T>> {
        val future = CompletableFuture<PollResult<T>>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        scheduler.scheduleWithFixedDelay(
            {
                fetchStatus(id)
                    .thenCompose { status ->
                        if (status == null) {
                            notifyError(operationTitle, "Failed to get operation status", project = project)
                            future.complete(PollResult.Failed("Failed to get status"))
                            scheduler.shutdown()
                            return@thenCompose CompletableFuture.completedFuture(null)
                        }

                        handleTerminalState(status, id)
                    }
                    .thenAccept { result ->
                        @Suppress("UNCHECKED_CAST")
                        val typedResult = result as? PollResult<T>
                        if (typedResult != null) {
                            future.complete(typedResult)
                            scheduler.shutdown()
                        }
                    }
                    .exceptionally { error ->
                        notifyError(operationTitle, error.message ?: "Unknown error", project = project)
                        future.complete(PollResult.Failed(error.message))
                        scheduler.shutdown()
                        null
                    }
            },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
        return future
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
