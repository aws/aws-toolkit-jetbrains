// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService

class CodeWhispererPopupEscHandler(sessionContext: SessionContext) : CodeWhispererEditorActionHandler(sessionContext) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        CodeWhispererService.getInstance().disposeDisplaySession(false)
    }
}
