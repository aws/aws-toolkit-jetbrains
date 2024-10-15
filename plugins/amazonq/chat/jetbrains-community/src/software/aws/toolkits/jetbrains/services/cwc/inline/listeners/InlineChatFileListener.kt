// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class InlineChatFileListener(project: Project) : FileEditorManagerListener {
    private var currentEditor: Editor? = null
    private var caretListener: ChatCaretListener? = null
    private var selectionListener: InlineChatSelectionListener? = null

    init {
        val editor = project.let { FileEditorManager.getInstance(it).selectedTextEditor }
        if (editor != null) {
            setupListenersForEditor(editor)
            currentEditor = editor
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val newEditor = (event.newEditor as? TextEditor)?.editor
        if (newEditor != null && newEditor != currentEditor) {
            currentEditor?.let { removeListenersFromCurrentEditor(it) }
            setupListenersForEditor(newEditor)
            currentEditor = newEditor
        }
    }

    private fun setupListenersForEditor(editor: Editor) {
        caretListener = ChatCaretListener().also { listener ->
            editor.caretModel.addCaretListener(listener)
        }

        selectionListener = InlineChatSelectionListener().also { listener ->
            editor.selectionModel.addSelectionListener(listener)
        }
    }

    private fun removeListenersFromCurrentEditor(editor: Editor) {
        caretListener?.let { listener ->
            editor.caretModel.removeCaretListener(listener)
        }
        selectionListener?.let { listener ->
            editor.selectionModel.removeSelectionListener(listener)
            listener.dispose()
        }
        caretListener = null
        selectionListener = null
    }

    fun dispose() {
        currentEditor?.let { removeListenersFromCurrentEditor(it) }
        currentEditor = null
    }
}
