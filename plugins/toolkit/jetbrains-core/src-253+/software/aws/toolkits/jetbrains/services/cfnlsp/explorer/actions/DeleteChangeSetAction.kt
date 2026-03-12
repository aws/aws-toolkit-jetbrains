// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ChangeSetNode
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetDeletionWorkflow

internal class DeleteChangeSetAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val node = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
            ?.filterIsInstance<ChangeSetNode>()?.firstOrNull() ?: return

        if (Messages.showYesNoDialog(project, "Delete change set '${node.changeSetName}'?", "Delete Change Set", null) == Messages.YES) {
            ChangeSetDeletionWorkflow(project).delete(node.stackName, node.changeSetName)
        }
    }
}
