// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

object ConnectionActionToolbarBuilder {
    fun createToolbar(targetComponent: JComponent, group: DefaultActionGroup): JComponent {
        return ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true).apply {
            layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
            setTargetComponent(targetComponent)
        }.component
    }
}
