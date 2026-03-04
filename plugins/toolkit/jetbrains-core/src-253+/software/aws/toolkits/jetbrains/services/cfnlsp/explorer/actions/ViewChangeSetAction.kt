// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ChangeSetNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeChangeSetParams
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ChangeSetDiffPanel
import software.aws.toolkits.jetbrains.utils.notifyError

internal class ViewChangeSetAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val node = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
            ?.filterIsInstance<ChangeSetNode>()?.firstOrNull() ?: return

        CfnClientService.getInstance(project)
            .describeChangeSet(DescribeChangeSetParams(node.changeSetName, node.stackName))
            .thenAccept { result ->
                if (result == null) {
                    notifyError("CloudFormation", "Failed to describe change set", project = project)
                    return@thenAccept
                }
                runInEdt {
                    ChangeSetDiffPanel.show(
                        project = project,
                        stackName = node.stackName,
                        changeSetName = node.changeSetName,
                        changes = result.changes ?: emptyList(),
                        enableDeploy = result.status == "CREATE_COMPLETE",
                        status = result.status,
                        creationTime = result.creationTime,
                        description = result.description,
                    )
                }
            }
    }
}
