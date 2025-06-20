// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.TextDocumentIdentifier
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ACTIVE_EDITOR_CHANGED_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.editor.ActiveEditorChangedParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ActiveEditorChangeListener(
    private val project: Project,
    private val executor: ScheduledExecutorService,
) : Disposable {
    private var debounceTask: ScheduledFuture<*>? = null
    private val debounceDelayMs = 100L

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    handleActiveEditorChange(event.newFile, event.newEditor?.let { FileEditorManager.getInstance(project).selectedTextEditor })
                }
            }
        )
    }

    private fun handleActiveEditorChange(file: VirtualFile?, editor: Editor?) {
        // Cancel any pending notification
        debounceTask?.cancel(false)

        // Schedule a new notification after the debounce period
        debounceTask = executor.schedule({
            try {
                val textDocument = file?.let { LspEditorUtil.toUriString(it) }?.let { TextDocumentIdentifier(it) }
                val cursorState = editor?.let { LspEditorUtil.getCursorState(it) }

                val params = ActiveEditorChangedParams(textDocument, cursorState)

                // Send notification to the language server
                ApplicationManager.getApplication().invokeLater {
                    AmazonQLspService.executeIfRunning(project) { _ ->
                        rawEndpoint.notify(ACTIVE_EDITOR_CHANGED_NOTIFICATION, params)
                    }
                }
            } catch (e: Exception) {
                LOG.warn(e) { "Failed to send active editor changed notification" }
            }
        }, debounceDelayMs, TimeUnit.MILLISECONDS)
    }

    override fun dispose() {
        debounceTask?.cancel(true)
    }

    companion object {
        private val LOG = getLogger<ActiveEditorChangeListener>()

        fun register(project: Project, executor: ScheduledExecutorService): ActiveEditorChangeListener {
            val listener = ActiveEditorChangeListener(project, executor)
            return listener
        }
    }
}
