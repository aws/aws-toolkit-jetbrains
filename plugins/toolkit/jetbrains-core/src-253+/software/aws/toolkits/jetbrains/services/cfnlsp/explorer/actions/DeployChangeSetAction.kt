// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ChangeSetNode
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.DeploymentWorkflow

internal class DeployChangeSetAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val node = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
            ?.filterIsInstance<ChangeSetNode>()?.firstOrNull() ?: return

        DeploymentWorkflow(project).deploy(node.stackName, node.changeSetName)
    }

    override fun update(e: AnActionEvent) {
        val node = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
            ?.filterIsInstance<ChangeSetNode>()?.firstOrNull()
        e.presentation.isEnabled = node?.status == "CREATE_COMPLETE"
    }
}
