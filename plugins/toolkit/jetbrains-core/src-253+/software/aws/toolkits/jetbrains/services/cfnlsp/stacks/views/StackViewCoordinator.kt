// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface StackSelectionListener {
    fun onStackChanged(stackArn: String, stackName: String?, isChangeSetMode: Boolean)
}

interface StackStatusListener {
    fun onStackStatusChanged(stackArn: String, status: String?)
}

interface StackPanelListener : StackSelectionListener, StackStatusListener

data class StackState(
    val stackName: String,
    val stackArn: String,
    val status: String?,
    val isChangeSetMode: Boolean,
    val lastUpdated: Instant
)

@Service(Service.Level.PROJECT)
class StackViewCoordinator : Disposable {
    private val stackStates = ConcurrentHashMap<String, StackState>()
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<StackPanelListener>>()

    fun setStack(stackArn: String, stackName: String, isChangeSetMode: Boolean = false) {
        LOG.info("Setting stack: $stackName (ARN: $stackArn)")
        val state = StackState(stackName, stackArn, null, isChangeSetMode, Instant.now())
        stackStates[stackArn] = state
        notifyStackChanged(stackArn)
    }

    fun updateStackStatus(stackArn: String, status: String) {
        LOG.info("Updating stack status: $stackArn -> $status")
        stackStates[stackArn]?.let { currentState ->
            if (currentState.status != status) {
                stackStates[stackArn] = currentState.copy(status = status, lastUpdated = Instant.now())
                notifyStatusChanged(stackArn, status)
            } else {
                LOG.debug("Status unchanged for $stackArn: $status")
            }
        } ?: LOG.warn("Stack not found for status update: $stackArn")
    }

    fun getStackState(stackArn: String): StackState? = stackStates[stackArn]

    fun removeStack(stackArn: String) {
        LOG.info("Removing stack: $stackArn")
        stackStates.remove(stackArn)
        listeners.remove(stackArn)
    }

    fun addListener(stackArn: String, listener: StackPanelListener): Disposable {
        LOG.info("Adding listener for stack: $stackArn")
        listeners.computeIfAbsent(stackArn) { CopyOnWriteArrayList() }.add(listener)
        
        // Immediately notify new listener of current state
        stackStates[stackArn]?.let { state ->
            LOG.info("Notifying new listener of current state: ${state.stackName}")
            listener.onStackChanged(stackArn, state.stackName, state.isChangeSetMode)
            state.status?.let { status ->
                listener.onStackStatusChanged(stackArn, status)
            }
        }
        
        return Disposable { 
            LOG.info("Removing listener for stack: $stackArn")
            listeners[stackArn]?.remove(listener)
            if (listeners[stackArn]?.isEmpty() == true) {
                listeners.remove(stackArn)
            }
        }
    }

    private fun notifyStackChanged(stackArn: String) {
        val state = stackStates[stackArn] ?: return
        val listenerCount = listeners[stackArn]?.size ?: 0
        LOG.info("Notifying $listenerCount listeners of stack change: ${state.stackName}")
        listeners[stackArn]?.forEach { 
            it.onStackChanged(stackArn, state.stackName, state.isChangeSetMode) 
        }
    }

    private fun notifyStatusChanged(stackArn: String, status: String) {
        val listenerCount = listeners[stackArn]?.size ?: 0
        LOG.info("Notifying $listenerCount listeners of status change: $stackArn -> $status")
        listeners[stackArn]?.forEach { 
            it.onStackStatusChanged(stackArn, status) 
        }
    }

    override fun dispose() {
        LOG.info("Disposing coordinator with ${stackStates.size} stacks")
        stackStates.clear()
        listeners.clear()
    }

    companion object {
        private val LOG = getLogger<StackViewCoordinator>()
        fun getInstance(project: Project): StackViewCoordinator = project.service()
    }
}
