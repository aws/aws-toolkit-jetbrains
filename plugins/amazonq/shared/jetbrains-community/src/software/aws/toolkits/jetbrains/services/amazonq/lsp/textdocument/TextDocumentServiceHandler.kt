// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class TextDocumentServiceHandler(
    private val project: Project,
    private val serverInstance: Disposable,
) : FileDocumentManagerListener,
    FileEditorManagerListener,
    BulkFileListener {

    init {
        subscribeToFileEditorEvents()
        subscribeToDocumentEvents()
    }

    private fun subscribeToFileEditorEvents() {
        project.messageBus.connect(serverInstance).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            this
        )
    }

    private fun subscribeToDocumentEvents() {
        project.messageBus.connect(serverInstance).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )
    }

    override fun after(events: MutableList<out VFileEvent>) {
        pluginAwareExecuteOnPooledThread {
            events.filterIsInstance<VFileContentChangeEvent>().forEach { event ->
                didChange(event)
            }
        }
    }

    private fun executeIfRunning(project: Project, runnable: (AmazonQLanguageServer) -> Unit) =
        AmazonQLspService.getInstance(project).instance?.languageServer?.let { runnable(it) }

    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        executeIfRunning(project) {
            it.textDocumentService.didOpen(
                DidOpenTextDocumentParams().apply {
                    textDocument = TextDocumentItem().apply {
                        uri = file.url
                        text = file.inputStream.readAllBytes().decodeToString()
                    }
                }
            )
        }
    }

    override fun fileClosed(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        executeIfRunning(project) {
            it.textDocumentService.didClose(
                DidCloseTextDocumentParams().apply {
                    textDocument = TextDocumentIdentifier().apply {
                        uri = file.url
                    }
                }
            )
        }
    }

    private fun didChange(event: VFileContentChangeEvent) {
        val document = FileDocumentManager.getInstance().getCachedDocument(event.file) ?: return

        executeIfRunning(project) {
            it.textDocumentService.didChange(
                DidChangeTextDocumentParams().apply {
                    textDocument = VersionedTextDocumentIdentifier().apply {
                        uri = event.file.url
                        version = document.modificationStamp.toInt()
                    }
                    contentChanges = listOf(
                        TextDocumentContentChangeEvent().apply {
                            text = document.text
                        }
                    )
                }
            )
        }
    }

    private fun didSave() {
    }
}
