// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationOptions
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationsServerCapabilities
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceServerCapabilities
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
    private lateinit var mockApplication: Application
    private lateinit var mockInitializeResult: InitializeResult
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockWorkspaceService: WorkspaceService
    private lateinit var sut: WorkspaceServiceHandler

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

        // Mock InitializeResult with file operation patterns
        mockInitializeResult = mockk<InitializeResult>()
        val mockCapabilities = mockk<ServerCapabilities>()
        val mockWorkspaceCapabilities = mockk<WorkspaceServerCapabilities>()
        val mockFileOperations = mockk<FileOperationsServerCapabilities>()

        val fileFilter = FileOperationFilter().apply {
            pattern = FileOperationPattern().apply {
                glob = "**/*.{ts,js,py,java}"
                matches = "file"
            }
        }
        val folderFilter = FileOperationFilter().apply {
            pattern = FileOperationPattern().apply {
                glob = "**/*"
                matches = "folder"
            }
        }

        val fileOperationOptions = FileOperationOptions().apply {
            filters = listOf(fileFilter, folderFilter)
        }

        every { mockFileOperations.didCreate } returns fileOperationOptions
        every { mockFileOperations.didDelete } returns fileOperationOptions
        every { mockFileOperations.didRename } returns fileOperationOptions
        every { mockWorkspaceCapabilities.fileOperations } returns mockFileOperations
        every { mockCapabilities.workspace } returns mockWorkspaceCapabilities
        every { mockInitializeResult.capabilities } returns mockCapabilities

        // Create WorkspaceServiceHandler with mocked InitializeResult
        sut = WorkspaceServiceHandler(project, mockInitializeResult, mockk())
    }

    @Test
    fun `test didCreateFiles with Python file`() = runTest {
        val pyUri = URI("file:///test/path")
        val pyEvent = createMockVFileEvent(pyUri, FileChangeType.Created, false, "py")

        sut.after(listOf(pyEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(pyUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with TypeScript file`() = runTest {
        val tsUri = URI("file:///test/path")
        val tsEvent = createMockVFileEvent(tsUri, FileChangeType.Created, false, "ts")

        sut.after(listOf(tsEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(tsUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with JavaScript file`() = runTest {
        val jsUri = URI("file:///test/path")
        val jsEvent = createMockVFileEvent(jsUri, FileChangeType.Created, false, "js")

        sut.after(listOf(jsEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(jsUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles with Java file`() = runTest {
        val javaUri = URI("file:///test/path")
        val javaEvent = createMockVFileEvent(javaUri, FileChangeType.Created, false, "java")

        sut.after(listOf(javaEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(javaUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles called for directory`() = runTest {
        val dirUri = URI("file:///test/directory/path")
        val dirEvent = createMockVFileEvent(dirUri, FileChangeType.Created, true, "")

        sut.after(listOf(dirEvent))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(dirUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didCreateFiles not called for unsupported file extension`() = runTest {
        val txtUri = URI("file:///test/path")
        val txtEvent = createMockVFileEvent(txtUri, FileChangeType.Created, false, "txt")

        sut.after(listOf(txtEvent))

        verify(exactly = 0) { mockWorkspaceService.didCreateFiles(any()) }
    }

    @Test
    fun `test didDeleteFiles with Python file`() = runTest {
        val pyUri = URI("file:///test/path")
        val pyEvent = createMockVFileEvent(pyUri, FileChangeType.Deleted, false, "py")

        sut.after(listOf(pyEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(pyUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with TypeScript file`() = runTest {
        val tsUri = URI("file:///test/path")
        val tsEvent = createMockVFileEvent(tsUri, FileChangeType.Deleted, false, "ts")

        sut.after(listOf(tsEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(tsUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with JavaScript file`() = runTest {
        val jsUri = URI("file:///test/path")
        val jsEvent = createMockVFileEvent(jsUri, FileChangeType.Deleted, false, "js")

        sut.after(listOf(jsEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(jsUri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with Java file`() = runTest {
        val javaUri = URI("file:///test/path")
        val javaEvent = createMockVFileEvent(javaUri, FileChangeType.Deleted, false, "java")

        sut.after(listOf(javaEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(javaUri.toString(), paramsSlot.captured.files[0].uri)
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
        assertEquals(dirUri.toString(), paramsSlot.captured.files[0].uri)
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
        assertEquals(createURI.toString(), paramsSlot.captured.changes[0].uri)
        assertEquals(FileChangeType.Created, paramsSlot.captured.changes[0].type)
        assertEquals(deleteURI.toString(), paramsSlot.captured.changes[1].uri)
        assertEquals(FileChangeType.Deleted, paramsSlot.captured.changes[1].type)
        assertEquals(changeURI.toString(), paramsSlot.captured.changes[2].uri)
        assertEquals(FileChangeType.Changed, paramsSlot.captured.changes[2].type)
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
            assertEquals("file:///test/$oldName", oldUri)
            assertEquals("file:///test/$newName", newUri)
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
            assertEquals("file:///test/oldDir", oldUri)
            assertEquals("file:///test/newDir", newUri)
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

    private fun createMockVFileEvent(uri: URI, type: FileChangeType = FileChangeType.Changed, isDirectory: Boolean, extension: String = "py"): VFileEvent {
        val virtualFile = mockk<VirtualFile>()
        val nioPath = mockk<Path>()

        every { virtualFile.isDirectory } returns isDirectory
        every { virtualFile.toNioPath() } returns nioPath
        every { nioPath.toUri() } returns uri
        every { virtualFile.path } returns "${uri.path}.$extension"

        return when (type) {
            FileChangeType.Deleted -> mockk<VFileDeleteEvent>()
            FileChangeType.Created -> mockk<VFileCreateEvent>()
            else -> mockk<VFileEvent>()
        }.apply {
            every { file } returns virtualFile
        }
    }

    // for didRename events
    private fun createMockPropertyChangeEvent(
        oldName: String,
        newName: String,
        isDirectory: Boolean = false,
    ): VFilePropertyChangeEvent {
        val file = mockk<VirtualFile>()
        val parent = mockk<VirtualFile>()
        val parentPath = mockk<Path>()
        val filePath = mockk<Path>()

        every { file.parent } returns parent
        every { parent.toNioPath() } returns parentPath
        every { file.toNioPath() } returns filePath
        every { file.isDirectory } returns isDirectory
        every { file.path } returns "/test/$newName"

        every { parentPath.resolve(oldName) } returns mockk {
            every { toUri() } returns URI("file:///test/$oldName")
        }
        every { filePath.toUri() } returns URI("file:///test/$newName")

        return mockk<VFilePropertyChangeEvent>().apply {
            every { propertyName } returns VirtualFile.PROP_NAME
            every { this@apply.file } returns file
            every { oldValue } returns oldName
            every { newValue } returns newName
        }
    }
}
