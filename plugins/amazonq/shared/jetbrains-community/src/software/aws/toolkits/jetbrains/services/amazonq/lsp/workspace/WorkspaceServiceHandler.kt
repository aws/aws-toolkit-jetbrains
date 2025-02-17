// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

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
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class WorkspaceServiceHandler(
    private val project: Project,
    serverInstance: Disposable
): BulkFileListener {

    init{
        project.messageBus.connect(serverInstance).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )
    }

    private fun executeIfRunning(project: Project, runnable: (AmazonQLanguageServer) -> Unit) =
        AmazonQLspService.getInstance(project).instance?.languageServer?.let { runnable(it) }

    private fun didCreateFiles(events: List<VFileEvent>){
        executeIfRunning(project) {
            if (events.isNotEmpty()) {
                it.workspaceService.didCreateFiles(
                    CreateFilesParams().apply {
                        files = events.map { event ->
                            FileCreate().apply {
                                uri = event.file?.toNioPath()?.toUri().toString()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun didDeleteFiles(events: List<VFileEvent>) {
        executeIfRunning(project) { languageServer ->
            if (events.isNotEmpty()) {
                languageServer.workspaceService.didDeleteFiles(
                    DeleteFilesParams().apply {
                        files = events.map { event ->
                            FileDelete().apply {
                                uri = event.file?.toNioPath()?.toUri().toString()
                            }
                        }
                    }
                )
            }
        }
    }

    override fun after(events: List<VFileEvent>) {
        // since we are using synchronous FileListener
        pluginAwareExecuteOnPooledThread {
            didCreateFiles(events.filterIsInstance<VFileCreateEvent>())
            didDeleteFiles(events.filterIsInstance<VFileDeleteEvent>())
        }
    }

    // still need to implement
    //private fun didChangeWorkspaceFolders() {
    //    languageServer.workspaceService.didChangeWorkspaceFolders()
    //}
}
