// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

interface StackSelectionListener {
    fun onStackChanged(stackName: String?, stackArn: String?, isChangeSetMode: Boolean)
}

interface StackStatusListener {
    fun onStackStatusChanged(status: String?)
}

interface StackPanelListener : StackSelectionListener, StackStatusListener

@Service(Service.Level.PROJECT)
class LspStackViewCoordinator : Disposable {
    private var currentStackName: String? = null
    private var currentStackArn: String? = null
    private var currentStackStatus: String? = null
    private var isChangeSetMode: Boolean = false

    private val listeners = CopyOnWriteArrayList<StackPanelListener>()

    fun setStack(stackName: String, stackArn: String, isChangeSetMode: Boolean = false) {
        currentStackName = stackName
        currentStackArn = stackArn
        this.isChangeSetMode = isChangeSetMode
        notifyStackChanged()
    }

    fun updateStackStatus(status: String) {
        if (currentStackStatus != status) {
            currentStackStatus = status
            notifyStatusChanged()
        }
    }

    fun getCurrentStackName(): String? = currentStackName

    fun addListener(listener: StackPanelListener): Disposable {
        listeners.add(listener)
        return Disposable { listeners.remove(listener) }
    }

    private fun notifyStackChanged() {
        listeners.forEach { it.onStackChanged(currentStackName, currentStackArn, isChangeSetMode) }
    }

    private fun notifyStatusChanged() {
        listeners.forEach { it.onStackStatusChanged(currentStackStatus) }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        fun getInstance(project: Project): LspStackViewCoordinator = project.service()
    }
}
