// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.UserDataHolderBase
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.QInlineCompletionProvider.Companion.DATA_KEY_Q_AUTO_TRIGGER_INTELLISENSE
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.QInlineCompletionProvider.Companion.KEY_Q_AUTO_TRIGGER_INTELLISENSE
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.QInlineCompletionProvider.Companion.Q_INLINE_PROVIDER_ID

fun InlineCompletionEvent.isManualCall(): Boolean =
    this is InlineCompletionEvent.ManualCall && this.additionalData.getUserData(KEY_Q_AUTO_TRIGGER_INTELLISENSE) == false

fun getManualCallEvent(editor: Editor, isIntelliSenseAccept: Boolean): InlineCompletionEvent {
    val data = UserDataHolderBase().apply { this.putUserData(KEY_Q_AUTO_TRIGGER_INTELLISENSE, isIntelliSenseAccept) }
    return InlineCompletionEvent.ManualCall(editor, Q_INLINE_PROVIDER_ID, data)
}

fun InlineCompletionEvent.isDeletion(): Boolean =
    this is InlineCompletionEvent.Backspace

