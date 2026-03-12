// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal interface StackStatusListener {
    fun onStackStatusUpdated()
}

internal interface StackPollingListener {
    fun onStackPolled()
}

data class StackState(
    val stackName: String,
    val stackArn: String,
    val status: String?,
    val lastUpdated: Instant,
)

@Service(Service.Level.PROJECT)
internal class StackViewCoordinator : Disposable {
    private val stackStates = ConcurrentHashMap<String, StackState>()
    private val statusListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<StackStatusListener>>()
    private val pollingListeners = ConcurrentHashMap<String, CopyOnWriteArrayList<StackPollingListener>>()

    fun setStack(stackArn: String, stackName: String) {
        val state = StackState(stackName, stackArn, null, Instant.now())
        stackStates[stackArn] = state
        notifyListeners(stackArn)
    }

    fun updateStackStatus(stackArn: String, status: String) {
        stackStates[stackArn]?.let { currentState ->
            if (currentState.status != status) {
                stackStates[stackArn] = currentState.copy(status = status, lastUpdated = Instant.now())
                notifyListeners(stackArn)
            }
            // Always notify polling listeners regardless of status change
            notifyPollingListeners(stackArn)
        } ?: LOG.warn { "Stack not found for status update: $stackArn" }
    }

    fun getStackState(stackArn: String): StackState? = stackStates[stackArn]

    fun removeStack(stackArn: String) {
        stackStates.remove(stackArn)
        statusListeners.remove(stackArn)
        pollingListeners.remove(stackArn)
    }

    fun addStatusListener(stackArn: String, listener: StackStatusListener): Disposable {
        statusListeners.computeIfAbsent(stackArn) { CopyOnWriteArrayList() }.add(listener)

        // Immediately notify new listener of current state
        stackStates[stackArn]?.let {
            listener.onStackStatusUpdated()
        }

        return Disposable {
            statusListeners[stackArn]?.remove(listener)
            if (statusListeners[stackArn]?.isEmpty() == true) {
                statusListeners.remove(stackArn)
            }
        }
    }

    fun addPollingListener(stackArn: String, listener: StackPollingListener): Disposable {
        pollingListeners.computeIfAbsent(stackArn) { CopyOnWriteArrayList() }.add(listener)

        return Disposable {
            pollingListeners[stackArn]?.remove(listener)
            if (pollingListeners[stackArn]?.isEmpty() == true) {
                pollingListeners.remove(stackArn)
            }
        }
    }

    private fun notifyListeners(stackArn: String) {
        statusListeners[stackArn]?.forEach {
            it.onStackStatusUpdated()
        }
    }

    private fun notifyPollingListeners(stackArn: String) {
        pollingListeners[stackArn]?.forEach {
            it.onStackPolled()
        }
    }

    override fun dispose() {
        stackStates.clear()
        statusListeners.clear()
        pollingListeners.clear()
    }

    companion object {
        private val LOG = getLogger<StackViewCoordinator>()
        fun getInstance(project: Project): StackViewCoordinator = project.service()
    }
}
