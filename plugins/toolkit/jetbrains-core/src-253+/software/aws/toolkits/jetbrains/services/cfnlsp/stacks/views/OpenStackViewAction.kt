// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StackNode
import software.aws.toolkits.resources.message

internal class OpenStackViewAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = message("cloudformation.stack.view")
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val stackNode = selectedNodes?.singleOrNull() as? StackNode
        e.presentation.isEnabledAndVisible = stackNode != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info("OpenStackViewAction triggered")
        val project = e.project ?: return
        val stackNode = getStackNode(e) ?: return

        val stackName = stackNode.stack.stackName ?: return
        val stackId = stackNode.stack.stackId ?: return

        LOG.info("Opening stack view for: $stackName")
        StackDetailWindowManager.getInstance(project)
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
