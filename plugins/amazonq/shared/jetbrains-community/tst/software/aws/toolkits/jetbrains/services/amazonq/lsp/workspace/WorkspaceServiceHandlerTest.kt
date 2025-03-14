// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.WorkspaceFolderUtil
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

class WorkspaceServiceHandlerTest {
    private lateinit var project: Project
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockWorkspaceService: WorkspaceService
    private lateinit var sut: WorkspaceServiceHandler
    private lateinit var mockApplication: Application

    @BeforeEach
    fun setup() {
        project = mockk<Project>()
        mockWorkspaceService = mockk<WorkspaceService>()
        mockLanguageServer = mockk<AmazonQLanguageServer>()

        mockApplication = mockk<Application>()
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication
        every { mockApplication.executeOnPooledThread(any<Callable<*>>()) } answers {
            CompletableFuture.completedFuture(firstArg<Callable<*>>().call())
        }

        // Mock the LSP service
        val mockLspService = mockk<AmazonQLspService>()

        // Mock the service methods on Project
        every { project.getService(AmazonQLspService::class.java) } returns mockLspService
        every { project.serviceIfCreated<AmazonQLspService>() } returns mockLspService

        // Mock the LSP service's executeSync method as a suspend function
        every {
            mockLspService.executeSync<CompletableFuture<ResponseMessage>>(any())
        } coAnswers {
            val func = firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLspService, mockLanguageServer)
        }

        // Mock workspace service
        every { mockLanguageServer.workspaceService } returns mockWorkspaceService
        every { mockWorkspaceService.didCreateFiles(any()) } returns Unit
        every { mockWorkspaceService.didDeleteFiles(any()) } returns Unit
        every { mockWorkspaceService.didRenameFiles(any()) } returns Unit
        every { mockWorkspaceService.didChangeWatchedFiles(any()) } returns Unit
        every { mockWorkspaceService.didChangeWorkspaceFolders(any()) } returns Unit

        // Mock message bus
        val messageBus = mockk<MessageBus>()
        every { project.messageBus } returns messageBus
        val mockConnection = mockk<MessageBusConnection>()
        every { messageBus.connect(any<Disposable>()) } returns mockConnection
        every { mockConnection.subscribe(any(), any()) } just runs

        sut = WorkspaceServiceHandler(project, mockk())
    }

    @Test
    fun `test didCreateFiles with Python file`() = runTest {
        val pyUri = URI("file:///test/path")
        val pyEvent = createMockVFileEvent(pyUri, FileChangeType.Created, false, "py")

        sut.after(listOf(pyEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(pyUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with TypeScript file`() = runTest {
        val tsUri = URI("file:///test/path")
        val tsEvent = createMockVFileEvent(tsUri, FileChangeType.Created, false, "ts")

        sut.after(listOf(tsEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(tsUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with JavaScript file`() = runTest {
        val jsUri = URI("file:///test/path")
        val jsEvent = createMockVFileEvent(jsUri, FileChangeType.Created, false, "js")

        sut.after(listOf(jsEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(jsUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with Java file`() = runTest {
        val javaUri = URI("file:///test/path")
        val javaEvent = createMockVFileEvent(javaUri, FileChangeType.Created, false, "java")

        sut.after(listOf(javaEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(javaUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles called for directory`() = runTest {
        val dirUri = URI("file:///test/directory/path")
        val dirEvent = createMockVFileEvent(dirUri, FileChangeType.Created, true, "")

        sut.after(listOf(dirEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(dirUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles not called for unsupported file extension`() = runTest {
        val txtUri = URI("file:///test/path")
        val txtEvent = createMockVFileEvent(txtUri, FileChangeType.Created, false, "txt")

        sut.after(listOf(txtEvent))

        verify(exactly = 0) { mockWorkspaceService.didCreateFiles(any()) }
    }

    @Test
    fun `test didCreateFiles with move event`() = runTest {
        val oldUri = URI("file:///test/oldPath")
        val newUri = URI("file:///test/newPath")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "test.py")

        sut.after(listOf(moveEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(newUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with copy event`() = runTest {
        val originalUri = URI("file:///test/original")
        val newUri = URI("file:///test/new")
        val copyEvent = createMockVFileCopyEvent(originalUri, newUri, "test.py")

        sut.after(listOf(copyEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(newUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with Python file`() = runTest {
        val pyUri = URI("file:///test/path")
        val pyEvent = createMockVFileEvent(pyUri, FileChangeType.Deleted, false, "py")

        sut.after(listOf(pyEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(pyUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with TypeScript file`() = runTest {
        val tsUri = URI("file:///test/path")
        val tsEvent = createMockVFileEvent(tsUri, FileChangeType.Deleted, false, "ts")

        sut.after(listOf(tsEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(tsUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with JavaScript file`() = runTest {
        val jsUri = URI("file:///test/path")
        val jsEvent = createMockVFileEvent(jsUri, FileChangeType.Deleted, false, "js")

        sut.after(listOf(jsEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(jsUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with Java file`() = runTest {
        val javaUri = URI("file:///test/path")
        val javaEvent = createMockVFileEvent(javaUri, FileChangeType.Deleted, false, "java")

        sut.after(listOf(javaEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(javaUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles not called for unsupported file extension`() = runTest {
        val txtUri = URI("file:///test/path")
        val txtEvent = createMockVFileEvent(txtUri, FileChangeType.Deleted, false, "txt")

        sut.after(listOf(txtEvent))

        verify(exactly = 0) { mockWorkspaceService.didDeleteFiles(any()) }
    }

    @Test
    fun `test didDeleteFiles called for directory`() = runTest {
        val dirUri = URI("file:///test/directory/path")
        val dirEvent = createMockVFileEvent(dirUri, FileChangeType.Deleted, true, "")

        sut.after(listOf(dirEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(dirUri.toString()), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles handles both delete and move events in same batch`() = runTest {
        val deleteUri = URI("file:///test/deleteFile")
        val oldMoveUri = URI("file:///test/oldMoveFile")
        val newMoveUri = URI("file:///test/newMoveFile")

        val deleteEvent = createMockVFileEvent(deleteUri, FileChangeType.Deleted, false, "py")
        val moveEvent = createMockVFileMoveEvent(oldMoveUri, newMoveUri, "test.py")

        sut.after(listOf(deleteEvent, moveEvent))

        val deleteParamsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(deleteParamsSlot)) }
        assertEquals(2, deleteParamsSlot.captured.files.size)
        assertEquals(normalizeFileUri(deleteUri.toString()), deleteParamsSlot.captured.files[0].uri)
        assertEquals(normalizeFileUri(oldMoveUri.toString()), deleteParamsSlot.captured.files[1].uri)
    }

    @Test
    fun `test didDeleteFiles with move event of unsupported file type`() = runTest {
        val oldUri = URI("file:///test/oldPath")
        val newUri = URI("file:///test/newPath")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "test.txt")

        sut.after(listOf(moveEvent))

        verify(exactly = 0) { mockWorkspaceService.didDeleteFiles(any()) }
    }

    @Test
    fun `test didDeleteFiles with move event of directory`() = runTest {
        val oldUri = URI("file:///test/oldDir")
        val newUri = URI("file:///test/newDir")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "", true)

        sut.after(listOf(moveEvent))

        val deleteParamsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(deleteParamsSlot)) }
        assertEquals(normalizeFileUri(oldUri.toString()), deleteParamsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didChangeWatchedFiles with valid events`() = runTest {
        // Arrange
        val createURI = URI("file:///test/pathOfCreation")
        val deleteURI = URI("file:///test/pathOfDeletion")
        val changeURI = URI("file:///test/pathOfChange")

        val virtualFileCreate = createMockVFileEvent(createURI, FileChangeType.Created, false)
        val virtualFileDelete = createMockVFileEvent(deleteURI, FileChangeType.Deleted, false)
        val virtualFileChange = createMockVFileEvent(changeURI, FileChangeType.Changed, false)

        // Act
        sut.after(listOf(virtualFileCreate, virtualFileDelete, virtualFileChange))

        // Assert
        val paramsSlot = slot<DidChangeWatchedFilesParams>()
        verify { mockWorkspaceService.didChangeWatchedFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(createURI.toString()), paramsSlot.captured.changes[0].uri)
        assertEquals(FileChangeType.Created, paramsSlot.captured.changes[0].type)
        assertEquals(normalizeFileUri(deleteURI.toString()), paramsSlot.captured.changes[1].uri)
        assertEquals(FileChangeType.Deleted, paramsSlot.captured.changes[1].type)
        assertEquals(normalizeFileUri(changeURI.toString()), paramsSlot.captured.changes[2].uri)
        assertEquals(FileChangeType.Changed, paramsSlot.captured.changes[2].type)
    }

    @Test
    fun `test didChangeWatchedFiles with move event reports both delete and create`() = runTest {
        val oldUri = URI("file:///test/oldPath")
        val newUri = URI("file:///test/newPath")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "test.py")

        sut.after(listOf(moveEvent))

        val paramsSlot = slot<DidChangeWatchedFilesParams>()
        verify { mockWorkspaceService.didChangeWatchedFiles(capture(paramsSlot)) }

        assertEquals(2, paramsSlot.captured.changes.size)
        assertEquals(normalizeFileUri(oldUri.toString()), paramsSlot.captured.changes[0].uri)
        assertEquals(FileChangeType.Deleted, paramsSlot.captured.changes[0].type)
        assertEquals(normalizeFileUri(newUri.toString()), paramsSlot.captured.changes[1].uri)
        assertEquals(FileChangeType.Created, paramsSlot.captured.changes[1].type)
    }

    @Test
    fun `test didChangeWatchedFiles with copy event`() = runTest {
        val originalUri = URI("file:///test/original")
        val newUri = URI("file:///test/new")
        val copyEvent = createMockVFileCopyEvent(originalUri, newUri, "test.py")

        sut.after(listOf(copyEvent))

        val paramsSlot = slot<DidChangeWatchedFilesParams>()
        verify { mockWorkspaceService.didChangeWatchedFiles(capture(paramsSlot)) }
        assertEquals(normalizeFileUri(newUri.toString()), paramsSlot.captured.changes[0].uri)
        assertEquals(FileChangeType.Created, paramsSlot.captured.changes[0].type)
    }

    @Test
    fun `test no invoked messages when events are empty`() = runTest {
        // Act
        sut.after(emptyList())

        // Assert
        verify(exactly = 0) { mockWorkspaceService.didCreateFiles(any()) }
        verify(exactly = 0) { mockWorkspaceService.didDeleteFiles(any()) }
        verify(exactly = 0) { mockWorkspaceService.didChangeWatchedFiles(any()) }
    }

    @Test
    fun `test didRenameFiles with supported file`() = runTest {
        // Arrange
        val oldName = "oldFile.java"
        val newName = "newFile.java"
        val propertyEvent = createMockPropertyChangeEvent(
            oldName = oldName,
            newName = newName,
            isDirectory = false,
        )

        // Act
        sut.after(listOf(propertyEvent))

        // Assert
        val paramsSlot = slot<RenameFilesParams>()
        verify { mockWorkspaceService.didRenameFiles(capture(paramsSlot)) }
        with(paramsSlot.captured.files[0]) {
            assertEquals(normalizeFileUri("file:///test/$oldName"), oldUri)
            assertEquals(normalizeFileUri("file:///test/$newName"), newUri)
        }
    }

    @Test
    fun `test didRenameFiles with unsupported file type`() = runTest {
        // Arrange
        val propertyEvent = createMockPropertyChangeEvent(
            oldName = "oldFile.txt",
            newName = "newFile.txt",
            isDirectory = false,
        )

        // Act
        sut.after(listOf(propertyEvent))

        // Assert
        verify(exactly = 0) { mockWorkspaceService.didRenameFiles(any()) }
    }

    @Test
    fun `test didRenameFiles with directory`() = runTest {
        // Arrange
        val propertyEvent = createMockPropertyChangeEvent(
            oldName = "oldDir",
            newName = "newDir",
            isDirectory = true
        )

        // Act
        sut.after(listOf(propertyEvent))

        // Assert
        val paramsSlot = slot<RenameFilesParams>()
        verify { mockWorkspaceService.didRenameFiles(capture(paramsSlot)) }
        with(paramsSlot.captured.files[0]) {
            assertEquals(normalizeFileUri("file:///test/oldDir"), oldUri)
            assertEquals(normalizeFileUri("file:///test/newDir"), newUri)
        }
    }

    @Test
    fun `test didRenameFiles with multiple files`() = runTest {
        // Arrange
        val event1 = createMockPropertyChangeEvent(
            oldName = "old1.java",
            newName = "new1.java",
        )
        val event2 = createMockPropertyChangeEvent(
            oldName = "old2.py",
            newName = "new2.py",
        )

        // Act
        sut.after(listOf(event1, event2))

        // Assert
        val paramsSlot = slot<RenameFilesParams>()
        verify { mockWorkspaceService.didRenameFiles(capture(paramsSlot)) }
        assertEquals(2, paramsSlot.captured.files.size)
    }

    @Test
    fun `rootsChanged does not notify when no changes`() = runTest {
        // Arrange
        mockkObject(WorkspaceFolderUtil)
        val folders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            }
        )
        every { WorkspaceFolderUtil.createWorkspaceFolders(any()) } returns folders

        // Act
        sut.beforeRootsChange(mockk())
        sut.rootsChanged(mockk())

        // Assert
        verify(exactly = 0) { mockWorkspaceService.didChangeWorkspaceFolders(any()) }
    }

    // rootsChanged handles
    @Test
    fun `rootsChanged handles init`() = runTest {
        // Arrange
        mockkObject(WorkspaceFolderUtil)
        val oldFolders = emptyList<WorkspaceFolder>()
        val newFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            }
        )

        // Act
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns oldFolders
        sut.beforeRootsChange(mockk())
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns newFolders
        sut.rootsChanged(mockk())

        // Assert
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertEquals(1, paramsSlot.captured.event.added.size)
        assertEquals("folder1", paramsSlot.captured.event.added[0].name)
    }

    // rootsChanged handles additional files added to root
    @Test
    fun `rootsChanged handles additional files added to root`() = runTest {
        // Arrange
        mockkObject(WorkspaceFolderUtil)
        val oldFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            }
        )
        val newFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            },
            WorkspaceFolder().apply {
                name = "folder2"
                uri = "file:///path/to/folder2"
            }
        )

        // Act
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns oldFolders
        sut.beforeRootsChange(mockk())
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns newFolders
        sut.rootsChanged(mockk())

        // Assert
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertEquals(1, paramsSlot.captured.event.added.size)
        assertEquals("folder2", paramsSlot.captured.event.added[0].name)
    }

    // rootsChanged handles removal of files from root
    @Test
    fun `rootsChanged handles removal of files from root`() = runTest {
        // Arrange
        mockkObject(WorkspaceFolderUtil)
        val oldFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            },
            WorkspaceFolder().apply {
                name = "folder2"
                uri = "file:///path/to/folder2"
            }
        )
        val newFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            }
        )

        // Act
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns oldFolders
        sut.beforeRootsChange(mockk())
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns newFolders
        sut.rootsChanged(mockk())

        // Assert
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertEquals(1, paramsSlot.captured.event.removed.size)
        assertEquals("folder2", paramsSlot.captured.event.removed[0].name)
    }

    @Test
    fun `rootsChanged handles multiple simultaneous additions and removals`() = runTest {
        // Arrange
        mockkObject(WorkspaceFolderUtil)
        val oldFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            },
            WorkspaceFolder().apply {
                name = "folder2"
                uri = "file:///path/to/folder2"
            }
        )
        val newFolders = listOf(
            WorkspaceFolder().apply {
                name = "folder1"
                uri = "file:///path/to/folder1"
            },
            WorkspaceFolder().apply {
                name = "folder3"
                uri = "file:///path/to/folder3"
            }
        )

        // Act
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns oldFolders
        sut.beforeRootsChange(mockk())
        every { WorkspaceFolderUtil.createWorkspaceFolders(project) } returns newFolders
        sut.rootsChanged(mockk())

        // Assert
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertEquals(1, paramsSlot.captured.event.added.size)
        assertEquals(1, paramsSlot.captured.event.removed.size)
        assertEquals("folder3", paramsSlot.captured.event.added[0].name)
        assertEquals("folder2", paramsSlot.captured.event.removed[0].name)
    }

    private fun createMockVirtualFile(uri: URI, fileName: String, isDirectory: Boolean = false): VirtualFile {
        val nioPath = mockk<Path> {
            every { toUri() } returns uri
        }
        return mockk<VirtualFile> {
            every { this@mockk.isDirectory } returns isDirectory
            every { toNioPath() } returns nioPath
            every { url } returns uri.path
            every { path } returns "${uri.path}/$fileName"
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
        }
    }

    private fun createMockVFileEvent(
        uri: URI,
        type: FileChangeType = FileChangeType.Changed,
        isDirectory: Boolean = false,
        extension: String = "py",
    ): VFileEvent {
        val virtualFile = createMockVirtualFile(uri, "test.$extension", isDirectory)
        return when (type) {
            FileChangeType.Deleted -> mockk<VFileDeleteEvent>()
            FileChangeType.Created -> mockk<VFileCreateEvent>()
            else -> mockk<VFileEvent>()
        }.apply {
            every { file } returns virtualFile
        }
    }

    private fun createMockPropertyChangeEvent(
        oldName: String,
        newName: String,
        isDirectory: Boolean = false,
    ): VFilePropertyChangeEvent {
        val oldUri = URI("file:///test/$oldName")
        val newUri = URI("file:///test/$newName")
        val file = createMockVirtualFile(newUri, newName, isDirectory)
        every { file.parent } returns createMockVirtualFile(oldUri, oldName, isDirectory)

        return mockk<VFilePropertyChangeEvent>().apply {
            every { propertyName } returns VirtualFile.PROP_NAME
            every { this@apply.file } returns file
            every { oldValue } returns oldName
            every { newValue } returns newName
        }
    }

    private fun createMockVFileMoveEvent(oldUri: URI, newUri: URI, fileName: String, isDirectory: Boolean = false): VFileMoveEvent {
        val oldFile = createMockVirtualFile(oldUri, fileName, isDirectory)
        val newFile = createMockVirtualFile(newUri, fileName, isDirectory)
        return mockk<VFileMoveEvent>().apply {
            every { file } returns newFile
            every { oldPath } returns oldUri.path
            every { oldParent } returns oldFile
        }
    }

    private fun createMockVFileCopyEvent(originalUri: URI, newUri: URI, fileName: String): VFileCopyEvent {
        val newParent = mockk<VirtualFile> {
            every { findChild(any()) } returns createMockVirtualFile(newUri, fileName)
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
        }
        return mockk<VFileCopyEvent>().apply {
            every { file } returns createMockVirtualFile(originalUri, fileName)
            every { this@apply.newParent } returns newParent
            every { newChildName } returns fileName
        }
    }

    // for windows unit tests
    private fun normalizeFileUri(uri: String): String {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return uri
        }

        if (!uri.startsWith("file:///")) {
            return uri
        }

        val path = uri.substringAfter("file:///")
        return "file:///C:/$path"
    }
}
