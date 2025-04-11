// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorState
import kotlin.io.path.Path

fun getTextDocumentIdentifier(project: Project): TextDocumentIdentifier? {
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor ?: return null
    val filePath = Path(selectedEditor.file.path).toUri()
    return TextDocumentIdentifier(filePath.toString())
}

fun getCursorState(project: Project): CursorState? {
    return runReadAction {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@runReadAction null
        val selectionModel = editor.selectionModel
        val document = editor.document

        // Get start position
        val startOffset = selectionModel.selectionStart
        val startLine = document.getLineNumber(startOffset)
        val startColumn = startOffset - document.getLineStartOffset(startLine)

        // Get end position
        val endOffset = selectionModel.selectionEnd
        val endLine = document.getLineNumber(endOffset)
        val endColumn = endOffset - document.getLineStartOffset(endLine)

        return@runReadAction CursorState(
            Range(
                Position(startLine, startColumn),
                Position(endLine, endColumn)
            )
        )
    }
}
