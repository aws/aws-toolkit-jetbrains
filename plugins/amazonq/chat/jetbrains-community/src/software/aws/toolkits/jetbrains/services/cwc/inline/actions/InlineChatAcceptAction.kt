// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatController

class InlineChatAcceptAction : EditorAction(InlineChatAcceptHandler()) {
    class InlineChatAcceptHandler : EditorActionHandler() {
        private val originalTabAction = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_TAB)

        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            val project = editor.project
            if (project != null) {
                val controller = InlineChatController(editor, project)
                controller.diffAcceptHandler.invoke()
            } else {
                originalTabAction.execute(editor, caret, dataContext)
            }
        }

        override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
            val project = editor.project
            return if (project != null) {
                true
            } else {
                originalTabAction.isEnabled(editor, caret, dataContext)
            }
        }

        private fun shouldHandleCustomAction(editor: Editor, project: Project): Boolean {
            return true
//            InlineChatController(editor, project).getShouldShowActions()
        }
    }
}

