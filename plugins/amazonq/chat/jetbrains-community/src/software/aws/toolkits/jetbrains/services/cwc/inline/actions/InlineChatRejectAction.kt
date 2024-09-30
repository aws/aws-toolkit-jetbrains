// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatController

class InlineChatRejectAction  : AnAction() {
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        e.presentation.isEnabled = if (editor != null && project != null) {
            // Check if inline chat is active
            InlineChatController(editor, project).getShouldShowActions()
        } else false
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.editor?.project?.let {
            val controller = InlineChatController(e.editor!!, it)
            if(controller.getShouldShowActions()) {
                controller.diffRejectHandler.invoke()
            }
        }
    }
}


