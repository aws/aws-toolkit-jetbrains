// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.FileCreate
import org.eclipse.lsp4j.FileDelete
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class TextDocumentServiceHandler(
    private val project: Project,
    private val languageServer: AmazonQLanguageServer
) : Disposable {

    fun startTextDocumentServiceListeners() {

    }

    private fun didClose() {

    }

    private fun didOpen() {

    }
     private fun didChange() {

    }

    private fun didSave() {

    }

    override fun dispose() {
    }
}
