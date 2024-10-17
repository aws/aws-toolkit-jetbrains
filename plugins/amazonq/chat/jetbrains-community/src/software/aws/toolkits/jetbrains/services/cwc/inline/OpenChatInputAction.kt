// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor

class OpenChatInputAction : AnAction() {
    private var inlineChatController: InlineChatController? = null
    private var caretListener: CaretListener? = null
    override fun actionPerformed(e: AnActionEvent) {
        e.editor?.let { editor ->
            e.editor?.project?.let { project ->
                inlineChatController = InlineChatController.getInstance(project)
                inlineChatController?.initPopup(editor)

                caretListener = createCaretListener(editor)
                editor.caretModel.addCaretListener(caretListener!!)
            }
        }
    }

    private fun createCaretListener(editor: Editor): CaretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            inlineChatController?.disposePopup()

            editor.caretModel.removeCaretListener(this)
            caretListener = null
            inlineChatController = null
        }
    }
}
