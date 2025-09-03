// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatEditorHint
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend

class InlineChatSelectionListener : SelectionListener, Disposable {
    private val inlineChatEditorHint = InlineChatEditorHint()
    override fun selectionChanged(e: SelectionEvent) {
        if (isRunningOnRemoteBackend()) return
        val editor = e.editor
        val selectionModel = editor.selectionModel

        if (selectionModel.hasSelection()) {
            inlineChatEditorHint.show(editor)
        } else {
            inlineChatEditorHint.hide()
        }
    }

    override fun dispose() {
        inlineChatEditorHint.hide()
    }
}
