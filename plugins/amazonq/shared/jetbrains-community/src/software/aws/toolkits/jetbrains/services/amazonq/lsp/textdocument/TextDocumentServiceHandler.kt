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
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ACTIVE_EDITOR_CHANGED_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.getCursorState
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString

class TextDocumentServiceHandler(
    private val project: Project,
    private val cs: CoroutineScope,
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
        cs.launch {
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.selectedFiles.forEach { file ->
                handleFileOpened(file)
            }
        }
    }

    private fun handleFileOpened(file: VirtualFile) {
        if (file.getUserData(KEY_REAL_TIME_EDIT_LISTENER) == null) {
            val listener = object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    realTimeEdit(event)
                }
            }
            ApplicationManager.getApplication().runReadAction {
                FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(listener)
            }
            file.putUserData(KEY_REAL_TIME_EDIT_LISTENER, listener)

            Disposer.register(this) {
                ApplicationManager.getApplication().runReadAction {
                    val existingListener = file.getUserData(KEY_REAL_TIME_EDIT_LISTENER)
                    if (existingListener != null) {
                        tryOrNull { FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(existingListener) }
                        file.putUserData(KEY_REAL_TIME_EDIT_LISTENER, null)
                    }
                }
            }

            cs.launch {
                AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
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
        }
    }

    override fun beforeDocumentSaving(document: Document) {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
                val file = FileDocumentManager.getInstance().getFile(document) ?: return@executeAsyncIfRunning
                toUriString(file)?.let { uri ->
                    languageServer.textDocumentService.didSave(
                        DidSaveTextDocumentParams().apply {
                            textDocument = TextDocumentIdentifier().apply {
                                this.uri = uri
                            }
                            // TODO: should respect `textDocumentSync.save.includeText` server capability config
                            text = document.text
                        }
                    )
                }
            }
        }
    }

    override fun after(events: MutableList<out VFileEvent>) {
        events.filterIsInstance<VFileContentChangeEvent>().forEach { event ->
            val document = FileDocumentManager.getInstance().getCachedDocument(event.file) ?: return@forEach

            handleFileOpened(event.file)
            cs.launch {
                AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
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
        val listener = file.getUserData(KEY_REAL_TIME_EDIT_LISTENER)
        if (listener != null) {
            tryOrNull { FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(listener) }
            file.putUserData(KEY_REAL_TIME_EDIT_LISTENER, null)

            cs.launch {
                AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
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
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        handleActiveEditorChange(event.newEditor)
    }

    private fun handleActiveEditorChange(fileEditor: FileEditor?) {
        val editor = (fileEditor as? TextEditor)?.editor ?: return
        editor.virtualFile?.let { handleFileOpened(it) }

        // Extract text editor if it's a TextEditor, otherwise null
        val textDocumentIdentifier = TextDocumentIdentifier(toUriString(editor.virtualFile))
        val cursorState = getCursorState(editor)

        val params = mapOf(
            "textDocument" to textDocumentIdentifier,
            "cursorState" to cursorState
        )

        // Send notification to the language server
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { _ ->
                rawEndpoint.notify(ACTIVE_EDITOR_CHANGED_NOTIFICATION, params)
            }
        }
    }

    private fun realTimeEdit(event: DocumentEvent) {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
                val vFile = FileDocumentManager.getInstance().getFile(event.document) ?: return@executeAsyncIfRunning
                toUriString(vFile)?.let { uri ->
                    languageServer.textDocumentService.didChange(
                        DidChangeTextDocumentParams().apply {
                            textDocument = VersionedTextDocumentIdentifier().apply {
                                this.uri = uri
                                version = event.document.modificationStamp.toInt()
                            }
                            contentChanges = listOf(
                                TextDocumentContentChangeEvent().apply {
                                    text = event.document.text
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

    companion object {
        private val KEY_REAL_TIME_EDIT_LISTENER = Key.create<DocumentListener>("amazonq.textdocument.realtimeedit.listener")
    }
}
