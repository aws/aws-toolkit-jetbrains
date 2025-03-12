// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileCreate
import org.eclipse.lsp4j.FileDelete
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.WorkspaceFolderUtil.createWorkspaceFolders
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

class WorkspaceServiceHandler(
    private val project: Project,
    initializeResult: InitializeResult,
    serverInstance: Disposable,
) : BulkFileListener,
    ModuleRootListener {

    private var lastSnapshot: List<WorkspaceFolder> = emptyList()
    private val operationMatchers: MutableMap<FileOperationType, List<Pair<PathMatcher, String>>> = mutableMapOf()

    init {
        operationMatchers.putAll(initializePatterns(initializeResult))

        project.messageBus.connect(serverInstance).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )

        project.messageBus.connect(serverInstance).subscribe(
            ModuleRootListener.TOPIC,
            this
        )
    }

    enum class FileOperationType {
        CREATE,
        DELETE,
        RENAME,
    }

    private fun initializePatterns(initializeResult: InitializeResult): Map<FileOperationType, List<Pair<PathMatcher, String>>> {
        val patterns = mutableMapOf<FileOperationType, List<Pair<PathMatcher, String>>>()

        initializeResult.capabilities?.workspace?.fileOperations?.let { fileOps ->
            patterns[FileOperationType.CREATE] = createMatchers(fileOps.didCreate?.filters)
            patterns[FileOperationType.DELETE] = createMatchers(fileOps.didDelete?.filters)
            patterns[FileOperationType.RENAME] = createMatchers(fileOps.didRename?.filters)
        }

        return patterns
    }

    private fun createMatchers(filters: List<FileOperationFilter>?): List<Pair<PathMatcher, String>> =
        filters?.map { filter ->
            FileSystems.getDefault().getPathMatcher("glob:${filter.pattern.glob}") to filter.pattern.matches
        }.orEmpty()

    private fun shouldHandleFile(file: VirtualFile, operation: FileOperationType): Boolean {
        val matchers = operationMatchers[operation] ?: return false
        return matchers.any { (matcher, type) ->
            when (type) {
                "file" -> !file.isDirectory && matcher.matches(Paths.get(file.path))
                "folder" -> file.isDirectory && matcher.matches(Paths.get(file.path))
                else -> matcher.matches(Paths.get(file.path))
            }
        }
    }

    private fun didCreateFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val validFiles = events.mapNotNull { event ->
                val file = event.file?.takeIf { shouldHandleFile(it, FileOperationType.CREATE) } ?: return@mapNotNull null
                file.toNioPath().toUri().toString().takeIf { it.isNotEmpty() }?.let { uri ->
                    FileCreate().apply {
                        this.uri = uri
                    }
                }
            }

            if (validFiles.isNotEmpty()) {
                languageServer.workspaceService.didCreateFiles(
                    CreateFilesParams().apply {
                        files = validFiles
                    }
                )
            }
        }
    }

    private fun didDeleteFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val validFiles = events.mapNotNull { event ->
                val file = event.file?.takeIf { shouldHandleFile(it, FileOperationType.DELETE) } ?: return@mapNotNull null
                file.toNioPath().toUri().toString().takeIf { it.isNotEmpty() }?.let { uri ->
                    FileDelete().apply {
                        this.uri = uri
                    }
                }
            }

            if (validFiles.isNotEmpty()) {
                languageServer.workspaceService.didDeleteFiles(
                    DeleteFilesParams().apply {
                        files = validFiles
                    }
                )
            }
        }
    }

    private fun didRenameFiles(events: List<VFilePropertyChangeEvent>) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val validRenames = events
                .filter { it.propertyName == VirtualFile.PROP_NAME }
                .mapNotNull { event ->
                    val file = event.file.takeIf { shouldHandleFile(it, FileOperationType.RENAME) } ?: return@mapNotNull null
                    val oldName = event.oldValue as? String ?: return@mapNotNull null
                    if (event.newValue !is String) return@mapNotNull null

                    // Construct old and new URIs
                    val parentPath = file.parent?.toNioPath() ?: return@mapNotNull null
                    val oldUri = parentPath.resolve(oldName).toUri().toString()
                    val newUri = file.toNioPath().toUri().toString()

                    FileRename().apply {
                        this.oldUri = oldUri
                        this.newUri = newUri
                    }
                }

            if (validRenames.isNotEmpty()) {
                languageServer.workspaceService.didRenameFiles(
                    RenameFilesParams().apply {
                        files = validRenames
                    }
                )
            }
        }
    }

    private fun didChangeWatchedFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val validChanges = events.mapNotNull { event ->
                event.file?.toNioPath()?.toUri()?.toString()?.takeIf { it.isNotEmpty() }?.let { uri ->
                    FileEvent().apply {
                        this.uri = uri
                        type = when (event) {
                            is VFileCreateEvent -> FileChangeType.Created
                            is VFileDeleteEvent -> FileChangeType.Deleted
                            else -> FileChangeType.Changed
                        }
                    }
                }
            }

            if (validChanges.isNotEmpty()) {
                languageServer.workspaceService.didChangeWatchedFiles(
                    DidChangeWatchedFilesParams().apply {
                        changes = validChanges
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
            didRenameFiles(events.filterIsInstance<VFilePropertyChangeEvent>())
            didChangeWatchedFiles(events)
        }
    }

    override fun beforeRootsChange(event: ModuleRootEvent) {
        lastSnapshot = createWorkspaceFolders(project)
    }

    override fun rootsChanged(event: ModuleRootEvent) {
        AmazonQLspService.executeIfRunning(project) { languageServer ->
            val currentSnapshot = createWorkspaceFolders(project)
            val addedFolders = currentSnapshot.filter { folder -> lastSnapshot.none { it.uri == folder.uri } }
            val removedFolders = lastSnapshot.filter { folder -> currentSnapshot.none { it.uri == folder.uri } }

            if (addedFolders.isNotEmpty() || removedFolders.isNotEmpty()) {
                languageServer.workspaceService.didChangeWorkspaceFolders(
                    DidChangeWorkspaceFoldersParams().apply {
                        this.event = WorkspaceFoldersChangeEvent().apply {
                            added = addedFolders
                            removed = removedFolders
                        }
                    }
                )
            }

            lastSnapshot = currentSnapshot
        }
    }
}
