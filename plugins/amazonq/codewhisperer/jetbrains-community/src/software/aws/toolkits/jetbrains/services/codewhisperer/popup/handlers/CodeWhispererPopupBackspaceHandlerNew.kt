// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManagerNew

// TODO: to be removed
class CodeWhispererPopupBackspaceHandlerNew(
    private val defaultHandler: EditorActionHandler,
    sessionContext: SessionContextNew,
) : CodeWhispererEditorActionHandlerNew(sessionContext) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val popupManager = CodeWhispererPopupManagerNew.getInstance()

        popupManager.dontClosePopupAndRun {
            val oldOffset = editor.caretModel.offset
            defaultHandler.execute(editor, caret, dataContext)
            val newOffset = editor.caretModel.offset
            val newText = "a".repeat(oldOffset - newOffset)
            ApplicationManager.getApplication().messageBus.syncPublisher(
                CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED
            ).backspace(sessionContext, newText)
        }
    }
}
