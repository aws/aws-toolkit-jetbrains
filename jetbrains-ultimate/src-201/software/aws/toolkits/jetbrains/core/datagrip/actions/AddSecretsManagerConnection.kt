// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.datagrip.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleExplorerNodeAction
import software.aws.toolkits.jetbrains.services.rds.RdsParentNode

class AddSecretsManagerConnection : SingleExplorerNodeAction<RdsParentNode>("TODO localize"), DumbAware {
    override fun actionPerformed(node: RdsParentNode, e: AnActionEvent) {
        val dialogWrapper = SecretsManagerDialogWrapper(node.nodeProject)
        val ok = dialogWrapper.showAndGet()
        if (!ok) {
            return
        }
        val selected = dialogWrapper.selected() ?: throw IllegalStateException("TODO localize")

    }
}
