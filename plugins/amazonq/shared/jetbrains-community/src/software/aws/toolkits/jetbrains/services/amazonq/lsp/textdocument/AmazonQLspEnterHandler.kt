// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.codeInsight.editorActions.EnterHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class AmazonQLspEnterHandler(
    private val originalHandler: EditorActionHandler,
    private val textDocumentHandler: TextDocumentServiceHandler,
) : EnterHandler(originalHandler) {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        originalHandler.execute(editor, caret, dataContext)
        textDocumentHandler.handleInlineCompletion(editor)
    }
}
