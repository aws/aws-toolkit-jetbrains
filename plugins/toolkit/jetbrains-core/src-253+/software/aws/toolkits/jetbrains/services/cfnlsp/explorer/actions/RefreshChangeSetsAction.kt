// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StackChangeSetsNode
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager

internal class RefreshChangeSetsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val node = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
            ?.filterIsInstance<StackChangeSetsNode>()?.firstOrNull() ?: return

        ChangeSetsManager.getInstance(project).refreshChangeSets(node.stackName)
    }
}
