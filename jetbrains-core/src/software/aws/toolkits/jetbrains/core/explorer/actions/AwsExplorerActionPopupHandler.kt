// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.PopupHandler
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.jetbrains.ToolkitPlaces
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import java.awt.Component

class AwsExplorerActionPopupHandler(private val explorerNodeResolver: () -> AwsExplorerNode<*>?) : PopupHandler() {
    override fun invokePopup(comp: Component?, x: Int, y: Int) {
        buildPopup()?.component?.show(comp, x, y)
    }

    @VisibleForTesting
    internal fun buildPopup(): ActionPopupMenu? {
        // Build a right click menu based on the selected first node
        // All nodes must be the same type (e.g. all S3 buckets, or a service node)
        val explorerNode = explorerNodeResolver() ?: return null
        val actionGroup = DefaultActionGroup()
        AwsExplorerActionContributor.EP_NAME.forEachExtensionSafe {
            it.process(actionGroup, explorerNode)
        }
        return when {
            actionGroup.childrenCount > 0 -> ActionManager.getInstance().createActionPopupMenu(ToolkitPlaces.EXPLORER_TOOL_WINDOW, actionGroup)
            else -> null
        }
    }
}
