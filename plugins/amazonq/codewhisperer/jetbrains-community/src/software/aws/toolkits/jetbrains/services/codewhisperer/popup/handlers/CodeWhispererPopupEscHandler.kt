// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew

class CodeWhispererPopupEscHandler(states: InvocationContext) : CodeWhispererEditorActionHandler(states) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        states.popup?.let { CodeWhispererPopupManager.getInstance().cancelPopup(it) }
    }
}

class CodeWhispererPopupEscHandlerNew(sessionContext: SessionContextNew) : CodeWhispererEditorActionHandlerNew(sessionContext) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        CodeWhispererServiceNew.getInstance().disposeDisplaySession(false)
    }
}
