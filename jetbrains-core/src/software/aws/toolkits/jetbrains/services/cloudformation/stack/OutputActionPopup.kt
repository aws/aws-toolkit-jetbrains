// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.stack

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.PopupHandler
import software.aws.toolkits.jetbrains.ui.CopyAction
import software.aws.toolkits.resources.message
import java.awt.Component

internal class OutputActionPopup(private val selected: () -> SelectedOutput?) : PopupHandler() {
    private val actionManager = ActionManager.getInstance()
    override fun invokePopup(comp: Component?, x: Int, y: Int) {
        val selected = selected() ?: return
        val actionGroup = DefaultActionGroup(
            listOf(
                CopyAction(message("cloudformation.stack.outputs.key.copy"), selected.key),
                CopyAction(message("cloudformation.stack.outputs.value.copy"), selected.value),
                CopyAction(message("cloudformation.stack.outputs.export.copy"), selected.outputName)
            )
        )
        val popupMenu = actionManager.createActionPopupMenu(STACK_TOOL_WINDOW.id, actionGroup)
        popupMenu.component.show(comp, x, y)
    }
}

internal data class SelectedOutput(internal val key: String?, internal val value: String?, internal val outputName: String?)
