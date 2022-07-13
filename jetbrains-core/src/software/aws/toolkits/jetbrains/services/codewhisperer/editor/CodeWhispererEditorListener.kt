// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.messages.Topic
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus

class CodeWhispererEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = (event.editor as? EditorImpl) ?: return

        editor.document.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    CodeWhispererInvocationStatus.getInstance().documentChanged()
                    if (!event.isWholeTextReplaced) {
                        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_DOCUMENT_CHANGE).documentChanged(event)
                    }
                }
            },
            editor.disposable
        )
    }

    companion object {
        val CODEWHISPERER_DOCUMENT_CHANGE = Topic.create("document changes", CodeWhispererDocumentChangedListener::class.java)
    }
}

interface CodeWhispererDocumentChangedListener {
    fun documentChanged(event: DocumentEvent) {}

    fun documentChanged2(chars: CharSequence) {}
}
