// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.QInlineCompletionProvider.Companion.DATA_KEY_Q_AUTO_TRIGGER_INTELLISENSE

fun InlineCompletionEvent.isManualCall(): Boolean =
    this is InlineCompletionEvent.DirectCall && this.context?.getData(DATA_KEY_Q_AUTO_TRIGGER_INTELLISENSE) == false

fun getManualCallEvent(editor: Editor, isIntelliSenseAccept: Boolean): InlineCompletionEvent {
    val dataContext = DataContext { dataId ->
        when (dataId) {
            DATA_KEY_Q_AUTO_TRIGGER_INTELLISENSE.name -> isIntelliSenseAccept
            else -> null
        }
    }
    return InlineCompletionEvent.DirectCall(editor, editor.caretModel.currentCaret, dataContext)
}

@Suppress("FunctionOnlyReturningConstant")
fun InlineCompletionEvent.isDeletion(): Boolean = false
