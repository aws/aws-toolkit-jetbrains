// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.WorkspaceEdit
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorPosition
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorRange
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorState
import java.io.File
import java.net.URI
import java.net.URISyntaxException

object LspEditorUtil {

    fun toUriString(virtualFile: VirtualFile): String? {
        val protocol = virtualFile.fileSystem.protocol
        val uri = when (protocol) {
            "jar" -> VfsUtilCore.convertToURL(virtualFile.url)?.toExternalForm()
            "jrt" -> virtualFile.url
            else -> toUri(VfsUtilCore.virtualToIoFile(virtualFile)).toASCIIString()
        } ?: return null

        return if (virtualFile.isDirectory) {
            uri.trimEnd('/', '\\')
        } else {
            uri
        }
    }

    private fun toUri(file: File): URI {
        try {
            // URI scheme specified by language server protocol
            return URI("file", "", file.absoluteFile.toURI().path, null)
        } catch (e: URISyntaxException) {
            LOG.warn { "${e.localizedMessage}: $e" }
            return file.absoluteFile.toURI()
        }
    }

    /**
     * Works but is divergent from [FocusAreaContextExtrator]
     */
    fun getCursorState(editor: Editor): CursorState =
        runReadAction {
            val selectionModel = editor.selectionModel
            if (selectionModel.hasSelection()) {
                val selectedStartPos = editor.offsetToLogicalPosition(selectionModel.selectionStart)
                val selectedEndPos = editor.offsetToLogicalPosition(selectionModel.selectionEnd)

                return@runReadAction CursorRange(
                    Range(
                        Position(selectedStartPos.line, selectedStartPos.column),
                        Position(selectedEndPos.line, selectedEndPos.column)
                    )
                )
            } else {
                return@runReadAction CursorPosition(
                    getCursorPosition(editor)
                )
            }
        }

    fun getCursorPosition(editor: Editor): Position =
        runReadAction {
            Position(
                editor.caretModel.primaryCaret.logicalPosition.line,
                editor.caretModel.primaryCaret.logicalPosition.column
            )
        }

    fun applyWorkspaceEdit(project: Project, edit: WorkspaceEdit) {
        WriteCommandAction.runWriteCommandAction(project) {
            edit.documentChanges?.forEach { change ->
                if (change.isLeft) {
                    val textDocumentEdit = change.left
                    val file = VirtualFileManager.getInstance().findFileByUrl(textDocumentEdit.textDocument.uri)
                    file?.let {
                        val document = FileDocumentManager.getInstance().getDocument(it)
                        val editor = FileEditorManager.getInstance(project).getSelectedEditor(it)?.let { fileEditor ->
                            if (fileEditor is com.intellij.openapi.fileEditor.TextEditor) fileEditor.editor else null
                        }
                        document?.let { doc ->
                            textDocumentEdit.edits.forEach { textEdit ->
                                val startOffset = if (editor != null) {
                                    editor.logicalPositionToOffset(LogicalPosition(textEdit.range.start.line, textEdit.range.start.character))
                                } else {
                                    doc.getLineStartOffset(textEdit.range.start.line) + textEdit.range.start.character
                                }
                                val endOffset = if (editor != null) {
                                    editor.logicalPositionToOffset(LogicalPosition(textEdit.range.end.line, textEdit.range.end.character))
                                } else {
                                    doc.getLineStartOffset(textEdit.range.end.line) + textEdit.range.end.character
                                }
                                doc.replaceString(startOffset, endOffset, textEdit.newText)
                            }
                        }
                    }
                }
            } ?: edit.changes?.forEach { (uri, textEdits) ->
                val file = VirtualFileManager.getInstance().findFileByUrl(uri)
                file?.let {
                    val document = FileDocumentManager.getInstance().getDocument(it)
                    val editor = FileEditorManager.getInstance(project).getSelectedEditor(it)?.let { fileEditor ->
                        if (fileEditor is com.intellij.openapi.fileEditor.TextEditor) fileEditor.editor else null
                    }
                    document?.let { doc ->
                        textEdits.forEach { textEdit ->
                            val startOffset = if (editor != null) {
                                editor.logicalPositionToOffset(LogicalPosition(textEdit.range.start.line, textEdit.range.start.character))
                            } else {
                                doc.getLineStartOffset(textEdit.range.start.line) + textEdit.range.start.character
                            }
                            val endOffset = if (editor != null) {
                                editor.logicalPositionToOffset(LogicalPosition(textEdit.range.end.line, textEdit.range.end.character))
                            } else {
                                doc.getLineStartOffset(textEdit.range.end.line) + textEdit.range.end.character
                            }
                            doc.replaceString(startOffset, endOffset, textEdit.newText)
                        }
                    }
                }
            }
        }
    }

    private val LOG = getLogger<LspEditorUtil>()
}
