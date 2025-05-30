// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
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
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class TextDocumentServiceHandler(
    private val project: Project,
) : FileDocumentManagerListener,
    FileEditorManagerListener,
    BulkFileListener,
    DocumentListener,
    Disposable {
    init {
        // didOpen & didClose events
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            this
        )

        // didChange events
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )

        // didSave events
        project.messageBus.connect(this).subscribe(
            FileDocumentManagerListener.TOPIC,
            this
        )

        // open files on startup
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { file ->
            handleFileOpened(file)
        }
    }

    private fun handleFileOpened(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        realTimeEdit(event)
                    }
                },
                this
            )
        }
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            toUriString(file)?.let { uri ->
                pluginAwareExecuteOnPooledThread {
                    languageServer.textDocumentService.didOpen(
                        DidOpenTextDocumentParams().apply {
                            textDocument = TextDocumentItem().apply {
                                this.uri = uri
                                text = file.inputStream.readAllBytes().decodeToString()
                                languageId = file.fileType.name.lowercase()
                                version = file.modificationStamp.toInt()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun beforeDocumentSaving(document: Document) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val file = FileDocumentManager.getInstance().getFile(document) ?: return@executeIfRunning
            toUriString(file)?.let { uri ->
                pluginAwareExecuteOnPooledThread {
                    languageServer.textDocumentService.didSave(
                        DidSaveTextDocumentParams().apply {
                            textDocument = TextDocumentIdentifier().apply {
                                this.uri = uri
                            }
                            text = document.text
                        }
                    )
                }
            }
        }
    }

    override fun after(events: MutableList<out VFileEvent>) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            pluginAwareExecuteOnPooledThread {
                events.filterIsInstance<VFileContentChangeEvent>().forEach { event ->
                    val document = FileDocumentManager.getInstance().getCachedDocument(event.file) ?: return@forEach
                    toUriString(event.file)?.let { uri ->
                        languageServer.textDocumentService.didChange(
                            DidChangeTextDocumentParams().apply {
                                textDocument = VersionedTextDocumentIdentifier().apply {
                                    this.uri = uri
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
            }
        }
    }

    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        handleFileOpened(file)
    }

    override fun fileClosed(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            toUriString(file)?.let { uri ->
                languageServer.textDocumentService.didClose(
                    DidCloseTextDocumentParams().apply {
                        textDocument = TextDocumentIdentifier().apply {
                            this.uri = uri
                        }
                    }
                )
            }
        }
    }

    private fun realTimeEdit(event: DocumentEvent) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            pluginAwareExecuteOnPooledThread {
                val vFile = FileDocumentManager.getInstance().getFile(event.document) ?: return@pluginAwareExecuteOnPooledThread
                toUriString(vFile)?.let { uri ->
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@pluginAwareExecuteOnPooledThread
                    val logicalPosition = editor.offsetToLogicalPosition(event.offset)
                    val newLogicalPosition = editor.offsetToLogicalPosition(event.offset + event.newLength)
                    languageServer.textDocumentService.didChange(
                        DidChangeTextDocumentParams().apply {
                            textDocument = VersionedTextDocumentIdentifier().apply {
                                this.uri = uri
                                version = event.document.modificationStamp.toInt()
                            }
                            contentChanges = listOf(
                                TextDocumentContentChangeEvent().apply {
                                    text = event.newFragment.toString()
                                    range = Range(Position(logicalPosition.line, logicalPosition.column), Position(newLogicalPosition.line, newLogicalPosition.column))
                                }
                            )
                        }
                    )
                }
            }
        }
        // Process document changes here
    }

    override fun dispose() {
    }
}
