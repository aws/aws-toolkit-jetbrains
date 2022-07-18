// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.editor

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererCodeCoverageTracker
import software.aws.toolkits.telemetry.CodewhispererLanguage

class CodeWhispererEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = (event.editor as? EditorImpl) ?: return

        editor.document.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    CodeWhispererInvocationStatus.getInstance().documentChanged()
                    // When open a file for the first time, IDE will also emit DocumentEvent for loading with `isWholeTextReplaced = true`
                    // Added this condition to filter out those events
                    if (!event.isWholeTextReplaced) {
                        val file = FileDocumentManager.getInstance().getFile(event.document)
                        val lang = when (file?.extension) {
                            "py" -> CodewhispererLanguage.Python
                            "java" -> CodewhispererLanguage.Java
                            "js" -> CodewhispererLanguage.Javascript
                            else -> CodewhispererLanguage.Unknown
                        }
                        CodeWhispererCodeCoverageTracker.getInstance(lang).documentChanged(event)
                    }
                }
            },
            editor.disposable
        )
    }
}
