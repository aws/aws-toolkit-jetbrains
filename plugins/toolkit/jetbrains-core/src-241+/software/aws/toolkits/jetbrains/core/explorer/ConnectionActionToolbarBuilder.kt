// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.util.ui.components.BorderLayoutPanel

object ConnectionActionToolbarBuilder {
    fun createToolbar(toolWindow: AwsToolkitExplorerToolWindow, group: DefaultActionGroup): BorderLayoutPanel {
        return BorderLayoutPanel().apply {
            addToCenter(
                ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true).apply {
                    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
                    setTargetComponent(toolWindow)
                }.component
            )

            val actionManager = ActionManager.getInstance()
            val rightActionGroup = DefaultActionGroup(
                actionManager.getAction("aws.toolkit.toolwindow.credentials.rightGroup.more"),
                actionManager.getAction("aws.toolkit.toolwindow.credentials.rightGroup.help")
            )

            addToRight(
                ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, rightActionGroup, true).apply {
                    setTargetComponent(toolWindow.component)
                }.component
            )
        }
    }
}
