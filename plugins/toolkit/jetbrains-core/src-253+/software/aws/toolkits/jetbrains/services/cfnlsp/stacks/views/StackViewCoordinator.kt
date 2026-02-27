// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.getLogger
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal interface StackPanelListener {
    fun onStackUpdated()
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
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<StackPanelListener>>()

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
        } ?: LOG.warn("Stack not found for status update: $stackArn")
    }

    fun getStackState(stackArn: String): StackState? = stackStates[stackArn]

    fun removeStack(stackArn: String) {
        stackStates.remove(stackArn)
        listeners.remove(stackArn)
    }

    fun addListener(stackArn: String, listener: StackPanelListener): Disposable {
        listeners.computeIfAbsent(stackArn) { CopyOnWriteArrayList() }.add(listener)

        // Immediately notify new listener of current state
        stackStates[stackArn]?.let {
            listener.onStackUpdated()
        }

        return Disposable {
            listeners[stackArn]?.remove(listener)
            if (listeners[stackArn]?.isEmpty() == true) {
                listeners.remove(stackArn)
            }
        }
    }

    private fun notifyListeners(stackArn: String) {
        listeners[stackArn]?.forEach {
            it.onStackUpdated()
        }
    }

    override fun dispose() {
        stackStates.clear()
        listeners.clear()
    }

    companion object {
        private val LOG = getLogger<StackViewCoordinator>()
        fun getInstance(project: Project): StackViewCoordinator = project.service()
    }
}
