// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentEdit
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApplicationExtension::class)
class ApplyWorkspaceEditTest {

    private lateinit var project: Project
    private lateinit var virtualFileManager: VirtualFileManager
    private lateinit var fileDocumentManager: FileDocumentManager
    private lateinit var fileEditorManager: FileEditorManager

    @BeforeEach
    fun setUp() {
        virtualFileManager = mockk(relaxed = true)
        fileDocumentManager = mockk(relaxed = true)
        fileEditorManager = mockk(relaxed = true)
        project = mockk(relaxed = true)

        mockkStatic(WriteCommandAction::class)
        every { WriteCommandAction.runWriteCommandAction(any(), any<Runnable>()) } answers {
            secondArg<Runnable>().run()
        }

        mockkStatic(VirtualFileManager::getInstance)
        every { VirtualFileManager.getInstance() } returns virtualFileManager

        mockkStatic(FileDocumentManager::getInstance)
        every { FileDocumentManager.getInstance() } returns fileDocumentManager

        mockkStatic(FileEditorManager::getInstance)
        every { FileEditorManager.getInstance(any()) } returns fileEditorManager
    }

    @Test
    fun `test applyWorkspaceEdit with changes`() {
        val uri = "file:///test.kt"
        val document = mockk<Document>(relaxed = true)
        val file = mockk<VirtualFile>()
        val textEdit = TextEdit(Range(Position(0, 0), Position(0, 5)), "newText")

        every { virtualFileManager.findFileByUrl(uri) } returns file
        every { fileDocumentManager.getDocument(file) } returns document
        every { document.getLineStartOffset(0) } returns 0

        val workspaceEdit = WorkspaceEdit().apply {
            changes = mapOf(uri to listOf(textEdit))
        }

        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)

        verify { document.replaceString(0, 5, "newText") }
    }

    @Test
    fun `test applyWorkspaceEdit with documentChanges`() {
        val uri = "file:///test.kt"
        val document = mockk<Document>(relaxed = true)
        val file = mockk<VirtualFile>()
        val textEdit = TextEdit(Range(Position(0, 0), Position(0, 5)), "newText")

        every { virtualFileManager.findFileByUrl(uri) } returns file
        every { fileDocumentManager.getDocument(file) } returns document
        every { document.getLineStartOffset(0) } returns 0

        val versionedIdentifier = VersionedTextDocumentIdentifier(uri, 1)
        val textDocumentEdit = TextDocumentEdit(versionedIdentifier, listOf(textEdit))
        val workspaceEdit = WorkspaceEdit().apply {
            documentChanges = listOf(Either.forLeft(textDocumentEdit))
        }

        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)

        verify { document.replaceString(0, 5, "newText") }
    }

    @Test
    fun `test applyWorkspaceEdit with editor`() {
        val uri = "file:///test.kt"
        val document = mockk<Document>(relaxed = true)
        val file = mockk<VirtualFile>()
        val editor = mockk<Editor>()
        val textEditor = mockk<TextEditor>()

        every { virtualFileManager.findFileByUrl(uri) } returns file
        every { fileDocumentManager.getDocument(file) } returns document
        every { fileEditorManager.getSelectedEditor(file) } returns textEditor
        every { textEditor.editor } returns editor

        val positionSlot = slot<LogicalPosition>()
        every { editor.logicalPositionToOffset(capture(positionSlot)) } answers {
            if (positionSlot.captured.line == 0 && positionSlot.captured.column == 0) 0 else 5
        }

        val textEdit = TextEdit(Range(Position(0, 0), Position(0, 5)), "newText")
        val workspaceEdit = WorkspaceEdit().apply {
            changes = mapOf(uri to listOf(textEdit))
        }

        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)

        verify { document.replaceString(0, 5, "newText") }
    }

    @Test
    fun `test applyWorkspaceEdit with both changes and documentChanges`() {
        val uri1 = "file:///test1.kt"
        val uri2 = "file:///test2.kt"
        val document1 = mockk<Document>(relaxed = true)
        val document2 = mockk<Document>(relaxed = true)
        val file1 = mockk<VirtualFile>()
        val file2 = mockk<VirtualFile>()

        val textEdit1 = TextEdit(Range(Position(0, 0), Position(0, 5)), "newText1")
        val textEdit2 = TextEdit(Range(Position(1, 0), Position(1, 10)), "newText2")

        every { virtualFileManager.findFileByUrl(uri1) } returns file1
        every { virtualFileManager.findFileByUrl(uri2) } returns file2
        every { fileDocumentManager.getDocument(file1) } returns document1
        every { fileDocumentManager.getDocument(file2) } returns document2
        every { document1.getLineStartOffset(0) } returns 0
        every { document2.getLineStartOffset(1) } returns 100

        val versionedIdentifier = VersionedTextDocumentIdentifier(uri1, 1)
        val textDocumentEdit = TextDocumentEdit(versionedIdentifier, listOf(textEdit1))

        val workspaceEdit = WorkspaceEdit().apply {
            documentChanges = listOf(Either.forLeft(textDocumentEdit))
            changes = mapOf(uri2 to listOf(textEdit2))
        }

        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)

        verify { document1.replaceString(0, 5, "newText1") }
        verify { document2.replaceString(100, 110, "newText2") }
    }

    @Test
    fun `test applyWorkspaceEdit with multiple edits to same file`() {
        val uri = "file:///test.kt"
        val document = mockk<Document>(relaxed = true)
        val file = mockk<VirtualFile>()

        val textEdit1 = TextEdit(Range(Position(0, 0), Position(0, 5)), "newText1")
        val textEdit2 = TextEdit(Range(Position(1, 0), Position(1, 10)), "newText2")

        every { virtualFileManager.findFileByUrl(uri) } returns file
        every { fileDocumentManager.getDocument(file) } returns document
        every { document.getLineStartOffset(0) } returns 0
        every { document.getLineStartOffset(1) } returns 100

        val workspaceEdit = WorkspaceEdit().apply {
            changes = mapOf(uri to listOf(textEdit1, textEdit2))
        }

        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)

        verify { document.replaceString(0, 5, "newText1") }
        verify { document.replaceString(100, 110, "newText2") }
    }

    @Test
    fun `test applyWorkspaceEdit with empty edits`() {
        val workspaceEdit = WorkspaceEdit()
        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)

        // No verification needed - just ensuring no exceptions
    }

    @Test
    fun `test applyWorkspaceEdit with invalid file`() {
        val uri = "file:///nonexistent.kt"
        val textEdit = TextEdit(Range(Position(0, 0), Position(0, 5)), "newText")

        every { virtualFileManager.findFileByUrl(uri) } returns null

        val workspaceEdit = WorkspaceEdit().apply {
            changes = mapOf(uri to listOf(textEdit))
        }

        // Execute - should not throw exception
        LspEditorUtil.applyWorkspaceEdit(project, workspaceEdit)
    }
}
