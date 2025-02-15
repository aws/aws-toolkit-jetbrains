// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer

class TextDocumentServiceHandler(
    private val project: Project,
    private val languageServer: AmazonQLanguageServer,
    private val serverInstance: Disposable
) : FileEditorManagerListener {

    init {
        subscribeToFileEditorEvents()
    }

    private fun subscribeToFileEditorEvents() {
        project.messageBus.connect(serverInstance).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            this
        )
    }

    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile
    ) {
        languageServer.textDocumentService.didOpen(
            DidOpenTextDocumentParams().apply {
                textDocument = TextDocumentItem().apply {
                    uri = file.url
                    text = file.inputStream.readAllBytes().decodeToString()
                }
            }
        )
    }

    override fun fileClosed(
        source: FileEditorManager,
        file: VirtualFile
    ) {
        languageServer.textDocumentService.didClose(
            DidCloseTextDocumentParams().apply {
                textDocument = TextDocumentIdentifier().apply {
                    uri = file.url
                }
            }
        )
    }

     private fun didChange() {

    }

    private fun didSave() {

    }

}
