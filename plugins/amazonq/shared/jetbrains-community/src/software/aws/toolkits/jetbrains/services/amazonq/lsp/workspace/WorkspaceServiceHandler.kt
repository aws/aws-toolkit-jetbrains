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
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileCreate
import org.eclipse.lsp4j.FileDelete
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.WorkspaceFolderUtil.createWorkspaceFolders
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

class WorkspaceServiceHandler(
    private val project: Project,
    private val cs: CoroutineScope,
    initializeResult: InitializeResult,
) : BulkFileListener,
    ModuleRootListener,
    Disposable {

    private var lastSnapshot: List<WorkspaceFolder> = emptyList()
    private val operationMatchers: MutableMap<FileOperationType, List<Pair<PathMatcher, String>>> = mutableMapOf()

    init {
        operationMatchers.putAll(initializePatterns(initializeResult))

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            this
        )

        project.messageBus.connect(this).subscribe(
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

    private suspend fun didCreateFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
            val validFiles = events.mapNotNull { event ->
                when (event) {
                    is VFileCopyEvent -> {
                        val newFile = event.newParent.findChild(event.newChildName)?.takeIf { shouldHandleFile(it, FileOperationType.CREATE) }
                            ?: return@mapNotNull null
                        toUriString(newFile)?.let { uri ->
                            FileCreate().apply {
                                this.uri = uri
                            }
                        }
                    }
                    else -> {
                        val file = event.file?.takeIf { shouldHandleFile(it, FileOperationType.CREATE) }
                            ?: return@mapNotNull null
                        toUriString(file)?.let { uri ->
                            FileCreate().apply {
                                this.uri = uri
                            }
                        }
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

    private suspend fun didDeleteFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
            val validFiles = events.mapNotNull { event ->
                when (event) {
                    is VFileDeleteEvent -> {
                        val file = event.file.takeIf { shouldHandleFile(it, FileOperationType.DELETE) } ?: return@mapNotNull null
                        toUriString(file)
                    }
                    is VFileMoveEvent -> {
                        val oldFile = event.oldParent?.takeIf { shouldHandleFile(it, FileOperationType.DELETE) } ?: return@mapNotNull null
                        toUriString(oldFile)
                    }
                    else -> null
                }?.let { uri ->
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

    private suspend fun didRenameFiles(events: List<VFilePropertyChangeEvent>) {
        AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
            val validRenames = events
                .filter { it.propertyName == VirtualFile.PROP_NAME }
                .mapNotNull { event ->
                    val renamedFile = event.file.takeIf { shouldHandleFile(it, FileOperationType.RENAME) } ?: return@mapNotNull null
                    val oldFileName = event.oldValue as? String ?: return@mapNotNull null
                    val parentFile = renamedFile.parent ?: return@mapNotNull null

                    val oldUri = toUriString(parentFile)?.let { parentUri -> "$parentUri/$oldFileName" }
                    val newUri = toUriString(renamedFile)

                    if (!renamedFile.isDirectory) {
                        oldUri?.let { uri ->
                            languageServer.textDocumentService.didClose(
                                DidCloseTextDocumentParams().apply {
                                    textDocument = TextDocumentIdentifier().apply {
                                        this.uri = uri
                                    }
                                }
                            )
                        }

                        newUri?.let { uri ->
                            languageServer.textDocumentService.didOpen(
                                DidOpenTextDocumentParams().apply {
                                    textDocument = TextDocumentItem().apply {
                                        this.uri = uri
                                        text = renamedFile.inputStream.readAllBytes().decodeToString()
                                        languageId = renamedFile.fileType.name.lowercase()
                                        version = renamedFile.modificationStamp.toInt()
                                    }
                                }
                            )
                        }
                    }

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

    private suspend fun didChangeWatchedFiles(events: List<VFileEvent>) {
        AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
            val validChanges = events.flatMap { event ->
                when (event) {
                    is VFileCopyEvent -> {
                        event.newParent.findChild(event.newChildName)?.let { newFile ->
                            toUriString(newFile)?.let { uri ->
                                listOf(
                                    FileEvent().apply {
                                        this.uri = uri
                                        type = FileChangeType.Created
                                    }
                                )
                            }
                        }.orEmpty()
                    }
                    is VFileMoveEvent -> {
                        listOfNotNull(
                            toUriString(event.oldParent)?.let { oldUri ->
                                FileEvent().apply {
                                    uri = oldUri
                                    type = FileChangeType.Deleted
                                }
                            },
                            toUriString(event.file)?.let { newUri ->
                                FileEvent().apply {
                                    uri = newUri
                                    type = FileChangeType.Created
                                }
                            }
                        )
                    }
                    else -> {
                        event.file?.let { file ->
                            toUriString(file)?.let { uri ->
                                listOf(
                                    FileEvent().apply {
                                        this.uri = uri
                                        type = when (event) {
                                            is VFileCreateEvent -> FileChangeType.Created
                                            is VFileDeleteEvent -> FileChangeType.Deleted
                                            else -> FileChangeType.Changed
                                        }
                                    }
                                )
                            }
                        }.orEmpty()
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
        cs.launch {
            didCreateFiles(events.filter { it is VFileCreateEvent || it is VFileMoveEvent || it is VFileCopyEvent })
            didDeleteFiles(events.filter { it is VFileMoveEvent || it is VFileDeleteEvent })
            didRenameFiles(events.filterIsInstance<VFilePropertyChangeEvent>())
            didChangeWatchedFiles(events)
        }
    }

    override fun beforeRootsChange(event: ModuleRootEvent) {
        lastSnapshot = createWorkspaceFolders(project)
    }

    override fun rootsChanged(event: ModuleRootEvent) {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { languageServer ->
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

    override fun dispose() {
    }
}
