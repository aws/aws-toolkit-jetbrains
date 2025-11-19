// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
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
import org.eclipse.lsp4j.TextEdit
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
        // Use Path.toUri() for proper cross-platform URI handling
        // Fixes Windows MCP workspace configuration bug where malformed URIs
        // get interpreted as literal file paths during directory creation
        return file.toPath().toAbsolutePath().normalize().toUri()
    }

    private fun URI.isCompliant(): Boolean {
        if (!"file".equals(this.scheme, ignoreCase = true)) return true

        val path = this.rawPath ?: this.path.orEmpty()
        val noAuthority = this.authority.isNullOrEmpty()

        // If the authority component is empty, the path cannot begin with two slash characters ("//")
        return !(noAuthority && path.startsWith("//"))
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
                    applyEditsToFile(project, textDocumentEdit.textDocument.uri, textDocumentEdit.edits)
                }
            }

            edit.changes?.forEach { (uri, textEdits) ->
                applyEditsToFile(project, uri, textEdits)
            }
        }
    }

    private fun applyEditsToFile(project: Project, uri: String, textEdits: List<TextEdit>) {
        val file = VirtualFileManager.getInstance().findFileByUrl(uri) ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)?.let {
            if (it is com.intellij.openapi.fileEditor.TextEditor) it.editor else null
        }

        textEdits.forEach { textEdit ->
            val startOffset = calculateOffset(editor, document, textEdit.range.start)
            val endOffset = calculateOffset(editor, document, textEdit.range.end)
            document.replaceString(startOffset, endOffset, textEdit.newText)
        }
    }

    private fun calculateOffset(editor: Editor?, document: Document, position: Position): Int =
        editor?.logicalPositionToOffset(LogicalPosition(position.line, position.character)) ?: (document.getLineStartOffset(position.line) + position.character)

    private val LOG = getLogger<LspEditorUtil>()
}
