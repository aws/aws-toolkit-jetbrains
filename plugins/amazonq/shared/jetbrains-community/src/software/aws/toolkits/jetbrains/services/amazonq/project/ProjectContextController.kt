// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings

@Service(Service.Level.PROJECT)
class ProjectContextController(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val encoderServer: EncoderServer = EncoderServer(project)
    private val projectContextProvider: ProjectContextProvider = ProjectContextProvider(project, encoderServer, cs)
    init {
        cs.launch {
            if (CodeWhispererSettings.getInstance().isProjectContextEnabled()) {
                encoderServer.downloadArtifactsAndStartServer()
            }
        }

        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val createdFiles = events.filterIsInstance<VFileCreateEvent>().mapNotNull { it.file?.path }
                val deletedFiles = events.filterIsInstance<VFileDeleteEvent>().map { it.file.path }
                val updateFiles = events.filterIsInstance<VFileContentChangeEvent>().map { it.path }

                updateIndex(createdFiles, IndexUpdateMode.ADD)
                updateIndex(deletedFiles, IndexUpdateMode.REMOVE)
                updateIndex(updateFiles, IndexUpdateMode.UPDATE)
            }
        })
    }

    enum class IndexUpdateMode(val value: String) {
        UPDATE("update"),
        REMOVE("remove"),
        ADD("add")
    }

    fun getProjectContextIndexComplete() = projectContextProvider.isIndexComplete.get()

    fun queryChat(prompt: String): List<RelevantDocument> {
        try {
            return projectContextProvider.queryChat(prompt)
        } catch (e: Exception) {
            logger.warn { "error while querying for project context $e.message" }
            return emptyList()
        }
    }

    // TODO: should we make it suspend and how?
    fun queryInline(prompt: String, filePath: String): List<BM25Chunk> {
        try {
            return projectContextProvider.queryInline(prompt, filePath)
        } catch (e: Exception) {
            logger.warn { "error while querying for project context $e.message" }
            return emptyList()
        }
    }

    fun updateIndex(filePaths: List<String>, mode: IndexUpdateMode) {
        try {
            return projectContextProvider.updateIndex(filePaths, mode.value)
        } catch (e: Exception) {
            logger.warn { "error while updating index for project context $e.message" }
        }
    }

    override fun dispose() {
        Disposer.dispose(encoderServer)
        Disposer.dispose(projectContextProvider)
    }

    companion object {
        private val logger = getLogger<ProjectContextController>()
        fun getInstance(project: Project) = project.service<ProjectContextController>()
    }
}
