// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
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
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.FileUriUtil.toUriString
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class TextDocumentServiceHandler(
    private val project: Project,
    serverInstance: Disposable,
) : FileDocumentManagerListener,
    FileEditorManagerListener,
    BulkFileListener {

    init {
        // didOpen & didClose events
        project.messageBus.connect(serverInstance).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            this
        )

        // didChange events
        project.messageBus.connect(serverInstance).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )

        // didSave events
        project.messageBus.connect(serverInstance).subscribe(
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
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            toUriString(file)?.let { uri ->
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

    override fun beforeDocumentSaving(document: Document) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val file = FileDocumentManager.getInstance().getFile(document) ?: return@executeIfRunning
            toUriString(file)?.let { uri ->
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
}
