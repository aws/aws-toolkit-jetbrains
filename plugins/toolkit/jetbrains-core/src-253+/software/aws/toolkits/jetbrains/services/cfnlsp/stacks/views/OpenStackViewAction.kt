// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StackNode
import software.aws.toolkits.resources.message

internal class OpenStackViewAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = message("cloudformation.stack.view")
        val stackNode = getStackNode(e)
        e.presentation.isEnabledAndVisible = stackNode != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stackNode = getStackNode(e) ?: return

        if (stackNode.stack.stackName == null) {
            LOG.error("Stack name is null for stack node")
            return
        }
        if (stackNode.stack.stackId == null) {
            LOG.error("Stack ID is null for stack node")
            return
        }
        val stackName = stackNode.stack.stackName
        val stackId = stackNode.stack.stackId

        StackViewWindowManager.getInstance(project)
            .openStack(stackName, stackId)
    }

    private fun getStackNode(e: AnActionEvent): StackNode? {
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        return selectedNodes?.singleOrNull() as? StackNode
    }

    companion object {
        private val LOG = getLogger<OpenStackViewAction>()
    }
}
