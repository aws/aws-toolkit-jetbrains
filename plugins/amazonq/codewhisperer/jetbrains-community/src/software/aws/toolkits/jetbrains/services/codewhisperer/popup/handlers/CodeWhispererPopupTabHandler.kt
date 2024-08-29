// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.editorActions.ExpandLiveTemplateCustomAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager

class CodeWhispererPopupTabHandler(states: InvocationContext, private val sessionContext: SessionContext) : CodeWhispererEditorActionHandler(states) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        ApplicationManager.getApplication().messageBus.syncPublisher(
            CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED
        ).beforeAccept(states, sessionContext)
    }
}
