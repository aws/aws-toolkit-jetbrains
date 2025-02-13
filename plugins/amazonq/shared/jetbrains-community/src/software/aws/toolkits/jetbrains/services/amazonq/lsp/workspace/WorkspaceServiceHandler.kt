// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import kotlinx.coroutines.flow.onEach
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.FileCreate
import org.eclipse.lsp4j.FileDelete
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread

class WorkspaceServiceHandler(
    private val project: Project,
    private val languageServer: AmazonQLanguageServer
) : Disposable{

    fun startWorkspaceServiceListeners() {
        didCreateFiles()
        didDeleteFiles()
    }

    private fun didCreateFiles() {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val createEvents = events.filterIsInstance<VFileCreateEvent>()
                    // since we are using synchronous FileListener
                    pluginAwareExecuteOnPooledThread {
                        if (createEvents.isNotEmpty()) {
                            languageServer.workspaceService.didCreateFiles(
                                CreateFilesParams().apply {
                                    files = createEvents.map { event ->
                                        FileCreate().apply {
                                            uri = event.file?.toNioPath()?.toUri().toString()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    private fun didDeleteFiles() {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val deleteEvents = events.filterIsInstance<VFileDeleteEvent>()
                    pluginAwareExecuteOnPooledThread {
                        if (deleteEvents.isNotEmpty()) {
                            languageServer.workspaceService.didDeleteFiles(
                                DeleteFilesParams().apply {
                                    files = deleteEvents.map { event ->
                                        FileDelete().apply {
                                            uri = event.file.toNioPath().toUri().toString()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    override fun dispose() {
    }
}

