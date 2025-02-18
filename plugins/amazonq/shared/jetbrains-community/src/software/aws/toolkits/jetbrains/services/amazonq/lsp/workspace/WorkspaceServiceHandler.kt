// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.eclipse.lsp4j.*
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class WorkspaceServiceHandler(
    private val project: Project,
    serverInstance: Disposable,
) : BulkFileListener,
    ProjectManagerListener {

    init {
        project.messageBus.connect(serverInstance).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )

        project.messageBus.connect(serverInstance).subscribe(
            ProjectManager.TOPIC,
            this
        )
    }

    private fun didCreateFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeIfRunning(project) {
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
        AmazonQLspService.executeIfRunning(project) { languageServer ->
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
            didChangeWatchedFiles(events)
        }
    }

    private fun didChangeWatchedFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeIfRunning(project) {
            if (events.isNotEmpty()) {
                it.workspaceService.didChangeWatchedFiles(
                    DidChangeWatchedFilesParams().apply {
                        changes = events.map { event ->
                            FileEvent().apply {
                                uri = event.file?.toNioPath()?.toUri().toString()
                                type = when (event) {
                                    is VFileCreateEvent -> FileChangeType.Created
                                    is VFileDeleteEvent -> FileChangeType.Deleted
                                    else -> FileChangeType.Changed
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
