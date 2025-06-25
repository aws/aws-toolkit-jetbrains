// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileTypes.FileType
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
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
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
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
    private lateinit var mockTextDocumentService: TextDocumentService
    private lateinit var sut: WorkspaceServiceHandler

    // not ideal
    private lateinit var testScope: TestScope

    @BeforeEach
    fun setup() {
        project = mockk<Project>()
        mockWorkspaceService = mockk<WorkspaceService>()
        mockTextDocumentService = mockk<TextDocumentService>()
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
        coEvery {
            mockLspService.executeIfRunning<CompletableFuture<ResponseMessage>>(any())
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

        // Mock textDocument service (for didRename calls)
        every { mockLanguageServer.textDocumentService } returns mockTextDocumentService
        every { mockTextDocumentService.didOpen(any()) } returns Unit
        every { mockTextDocumentService.didClose(any()) } returns Unit

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
        testScope = TestScope()
        sut = WorkspaceServiceHandler(project, testScope, mockInitializeResult)
    }

    @Test
    fun `test didCreateFiles with Python file`() {
        val pyUri = URI("file:///test/path")
        val pyEvent = createMockVFileEvent(pyUri, FileChangeType.Created, false, "py")

        sut.after(listOf(pyEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(pyUri.toString()))
    }

    @Test
    fun `test didCreateFiles with TypeScript file`() {
        val tsUri = URI("file:///test/path")
        val tsEvent = createMockVFileEvent(tsUri, FileChangeType.Created, false, "ts")

        sut.after(listOf(tsEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(tsUri.toString()))
    }

    @Test
    fun `test didCreateFiles with JavaScript file`() {
        val jsUri = URI("file:///test/path")
        val jsEvent = createMockVFileEvent(jsUri, FileChangeType.Created, false, "js")

        sut.after(listOf(jsEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(jsUri.toString()))
    }

    @Test
    fun `test didCreateFiles with Java file`() {
        val javaUri = URI("file:///test/path")
        val javaEvent = createMockVFileEvent(javaUri, FileChangeType.Created, false, "java")

        sut.after(listOf(javaEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(javaUri.toString()))
    }

    @Test
    fun `test didCreateFiles called for directory`() {
        val dirUri = URI("file:///test/directory/path")
        val dirEvent = createMockVFileEvent(dirUri, FileChangeType.Created, true, "")

        sut.after(listOf(dirEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(dirUri.toString()))
    }

    @Test
    fun `test didCreateFiles not called for unsupported file extension`() {
        val txtUri = URI("file:///test/path")
        val txtEvent = createMockVFileEvent(txtUri, FileChangeType.Created, false, "txt")

        sut.after(listOf(txtEvent))

        testScope.advanceUntilIdle()
        verify(exactly = 0) { mockWorkspaceService.didCreateFiles(any()) }
    }

    @Test
    fun `test didCreateFiles with move event`() {
        val oldUri = URI("file:///test/oldPath")
        val newUri = URI("file:///test/newPath")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "test.py")

        sut.after(listOf(moveEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(newUri.toString()))
    }

    @Test
    fun `test didCreateFiles with copy event`() {
        val originalUri = URI("file:///test/original")
        val newUri = URI("file:///test/new")
        val copyEvent = createMockVFileCopyEvent(originalUri, newUri, "test.py")

        sut.after(listOf(copyEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(newUri.toString()))
    }

    @Test
    fun `test didDeleteFiles with Python file`() {
        val pyUri = URI("file:///test/path")
        val pyEvent = createMockVFileEvent(pyUri, FileChangeType.Deleted, false, "py")

        sut.after(listOf(pyEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(pyUri.toString()))
    }

    @Test
    fun `test didDeleteFiles with TypeScript file`() {
        val tsUri = URI("file:///test/path")
        val tsEvent = createMockVFileEvent(tsUri, FileChangeType.Deleted, false, "ts")

        sut.after(listOf(tsEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(tsUri.toString()))
    }

    @Test
    fun `test didDeleteFiles with JavaScript file`() {
        val jsUri = URI("file:///test/path")
        val jsEvent = createMockVFileEvent(jsUri, FileChangeType.Deleted, false, "js")

        sut.after(listOf(jsEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(jsUri.toString()))
    }

    @Test
    fun `test didDeleteFiles with Java file`() {
        val javaUri = URI("file:///test/path")
        val javaEvent = createMockVFileEvent(javaUri, FileChangeType.Deleted, false, "java")

        sut.after(listOf(javaEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(javaUri.toString()))
    }

    @Test
    fun `test didDeleteFiles not called for unsupported file extension`() {
        val txtUri = URI("file:///test/path")
        val txtEvent = createMockVFileEvent(txtUri, FileChangeType.Deleted, false, "txt")

        sut.after(listOf(txtEvent))

        testScope.advanceUntilIdle()
        verify(exactly = 0) { mockWorkspaceService.didDeleteFiles(any()) }
    }

    @Test
    fun `test didDeleteFiles called for directory`() {
        val dirUri = URI("file:///test/directory/path")
        val dirEvent = createMockVFileEvent(dirUri, FileChangeType.Deleted, true, "")

        sut.after(listOf(dirEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(dirUri.toString()))
    }

    @Test
    fun `test didDeleteFiles handles both delete and move events in same batch`() {
        val deleteUri = URI("file:///test/deleteFile")
        val oldMoveUri = URI("file:///test/oldMoveFile")
        val newMoveUri = URI("file:///test/newMoveFile")

        val deleteEvent = createMockVFileEvent(deleteUri, FileChangeType.Deleted, false, "py")
        val moveEvent = createMockVFileMoveEvent(oldMoveUri, newMoveUri, "test.py")

        sut.after(listOf(deleteEvent, moveEvent))

        testScope.advanceUntilIdle()
        val deleteParamsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(deleteParamsSlot)) }
        assertThat(deleteParamsSlot.captured.files).hasSize(2)
        assertThat(deleteParamsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(deleteUri.toString()))
        assertThat(deleteParamsSlot.captured.files[1].uri).isEqualTo(normalizeFileUri(oldMoveUri.toString()))
    }

    @Test
    fun `test didDeleteFiles with move event of unsupported file type`() {
        val oldUri = URI("file:///test/oldPath")
        val newUri = URI("file:///test/newPath")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "test.txt")

        sut.after(listOf(moveEvent))

        testScope.advanceUntilIdle()
        verify(exactly = 0) { mockWorkspaceService.didDeleteFiles(any()) }
    }

    @Test
    fun `test didDeleteFiles with move event of directory`() {
        val oldUri = URI("file:///test/oldDir")
        val newUri = URI("file:///test/newDir")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "", true)

        sut.after(listOf(moveEvent))

        testScope.advanceUntilIdle()
        val deleteParamsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(deleteParamsSlot)) }
        assertThat(deleteParamsSlot.captured.files[0].uri).isEqualTo(normalizeFileUri(oldUri.toString()))
    }

    @Test
    fun `test didChangeWatchedFiles with valid events`() {
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
        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWatchedFilesParams>()
        verify { mockWorkspaceService.didChangeWatchedFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.changes[0].uri).isEqualTo(normalizeFileUri(createURI.toString()))
        assertThat(paramsSlot.captured.changes[0].type).isEqualTo(FileChangeType.Created)
        assertThat(paramsSlot.captured.changes[1].uri).isEqualTo(normalizeFileUri(deleteURI.toString()))
        assertThat(paramsSlot.captured.changes[1].type).isEqualTo(FileChangeType.Deleted)
        assertThat(paramsSlot.captured.changes[2].uri).isEqualTo(normalizeFileUri(changeURI.toString()))
        assertThat(paramsSlot.captured.changes[2].type).isEqualTo(FileChangeType.Changed)
    }

    @Test
    fun `test didChangeWatchedFiles with move event reports both delete and create`() {
        val oldUri = URI("file:///test/oldPath")
        val newUri = URI("file:///test/newPath")
        val moveEvent = createMockVFileMoveEvent(oldUri, newUri, "test.py")

        sut.after(listOf(moveEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWatchedFilesParams>()
        verify { mockWorkspaceService.didChangeWatchedFiles(capture(paramsSlot)) }

        assertThat(paramsSlot.captured.changes).hasSize(2)
        assertThat(paramsSlot.captured.changes[0].uri).isEqualTo(normalizeFileUri(oldUri.toString()))
        assertThat(paramsSlot.captured.changes[0].type).isEqualTo(FileChangeType.Deleted)
        assertThat(paramsSlot.captured.changes[1].uri).isEqualTo(normalizeFileUri(newUri.toString()))
        assertThat(paramsSlot.captured.changes[1].type).isEqualTo(FileChangeType.Created)
    }

    @Test
    fun `test didChangeWatchedFiles with copy event`() {
        val originalUri = URI("file:///test/original")
        val newUri = URI("file:///test/new")
        val copyEvent = createMockVFileCopyEvent(originalUri, newUri, "test.py")

        sut.after(listOf(copyEvent))

        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWatchedFilesParams>()
        verify { mockWorkspaceService.didChangeWatchedFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.changes[0].uri).isEqualTo(normalizeFileUri(newUri.toString()))
        assertThat(paramsSlot.captured.changes[0].type).isEqualTo(FileChangeType.Created)
    }

    @Test
    fun `test no invoked messages when events are empty`() {
        // Act
        sut.after(emptyList())

        // Assert
        verify(exactly = 0) { mockWorkspaceService.didCreateFiles(any()) }
        verify(exactly = 0) { mockWorkspaceService.didDeleteFiles(any()) }
        verify(exactly = 0) { mockWorkspaceService.didChangeWatchedFiles(any()) }
    }

    @Test
    fun `test didRenameFiles with supported file`() {
        // Arrange
        val oldName = "oldFile.java"
        val newName = "newFile.java"
        val propertyEvent = createMockPropertyChangeEvent(
            oldName = oldName,
            newName = newName,
            isDirectory = false,
            fileTypeName = "JAVA",
            modificationStamp = 123L
        )

        // Act
        sut.after(listOf(propertyEvent))

        testScope.advanceUntilIdle()
        val closeParams = slot<DidCloseTextDocumentParams>()
        verify { mockTextDocumentService.didClose(capture(closeParams)) }
        assertThat(closeParams.captured.textDocument.uri).isEqualTo(normalizeFileUri("file:///testDir/$oldName"))

        val openParams = slot<DidOpenTextDocumentParams>()
        verify { mockTextDocumentService.didOpen(capture(openParams)) }
        with(openParams.captured.textDocument) {
            assertThat(uri).isEqualTo(normalizeFileUri("file:///testDir/$newName"))
            assertThat(text).isEqualTo("content")
            assertThat(languageId).isEqualTo("java")
            assertThat(version).isEqualTo(123)
        }

        // Assert
        val paramsSlot = slot<RenameFilesParams>()
        verify { mockWorkspaceService.didRenameFiles(capture(paramsSlot)) }
        with(paramsSlot.captured.files[0]) {
            assertThat(oldUri).isEqualTo(normalizeFileUri("file:///testDir/$oldName"))
            assertThat(newUri).isEqualTo(normalizeFileUri("file:///testDir/$newName"))
        }
    }

    @Test
    fun `test didRenameFiles with unsupported file type`() {
        // Arrange
        val propertyEvent = createMockPropertyChangeEvent(
            oldName = "oldFile.txt",
            newName = "newFile.txt",
            isDirectory = false,
        )

        // Act
        sut.after(listOf(propertyEvent))

        // Assert
        testScope.advanceUntilIdle()
        verify(exactly = 0) { mockTextDocumentService.didClose(any()) }
        verify(exactly = 0) { mockTextDocumentService.didOpen(any()) }
        verify(exactly = 0) { mockWorkspaceService.didRenameFiles(any()) }
    }

    @Test
    fun `test didRenameFiles with directory`() {
        // Arrange
        val propertyEvent = createMockPropertyChangeEvent(
            oldName = "oldDir",
            newName = "newDir",
            isDirectory = true
        )

        // Act
        sut.after(listOf(propertyEvent))

        // Assert
        testScope.advanceUntilIdle()
        verify(exactly = 0) { mockTextDocumentService.didClose(any()) }
        verify(exactly = 0) { mockTextDocumentService.didOpen(any()) }
        val paramsSlot = slot<RenameFilesParams>()
        verify { mockWorkspaceService.didRenameFiles(capture(paramsSlot)) }
        with(paramsSlot.captured.files[0]) {
            assertThat(oldUri).isEqualTo(normalizeFileUri("file:///testDir/oldDir"))
            assertThat(newUri).isEqualTo(normalizeFileUri("file:///testDir/newDir"))
        }
    }

    @Test
    fun `test didRenameFiles with multiple files`() {
        // Arrange
        val event1 = createMockPropertyChangeEvent(
            oldName = "old1.java",
            newName = "new1.java",
            fileTypeName = "JAVA",
            modificationStamp = 123L
        )
        val event2 = createMockPropertyChangeEvent(
            oldName = "old2.py",
            newName = "new2.py",
            fileTypeName = "Python",
            modificationStamp = 456L
        )

        // Act
        sut.after(listOf(event1, event2))

        // Assert
        testScope.advanceUntilIdle()
        val paramsSlot = slot<RenameFilesParams>()
        verify { mockWorkspaceService.didRenameFiles(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.files).hasSize(2)

        // Verify didClose and didOpen for both files
        verify(exactly = 2) { mockTextDocumentService.didClose(any()) }

        val openParamsSlot = mutableListOf<DidOpenTextDocumentParams>()
        verify(exactly = 2) { mockTextDocumentService.didOpen(capture(openParamsSlot)) }

        assertThat(openParamsSlot[0].textDocument.languageId).isEqualTo("java")
        assertThat(openParamsSlot[0].textDocument.version).isEqualTo(123)
        assertThat(openParamsSlot[1].textDocument.languageId).isEqualTo("python")
        assertThat(openParamsSlot[1].textDocument.version).isEqualTo(456)
    }

    @Test
    fun `rootsChanged does not notify when no changes`() {
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
        testScope.advanceUntilIdle()
        verify(exactly = 0) { mockWorkspaceService.didChangeWorkspaceFolders(any()) }
    }

    // rootsChanged handles
    @Test
    fun `rootsChanged handles init`() {
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
        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.event.added).hasSize(1)
        assertThat(paramsSlot.captured.event.added[0].name).isEqualTo("folder1")
    }

    // rootsChanged handles additional files added to root
    @Test
    fun `rootsChanged handles additional files added to root`() {
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
        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.event.added).hasSize(1)
        assertThat(paramsSlot.captured.event.added[0].name).isEqualTo("folder2")
    }

    // rootsChanged handles removal of files from root
    @Test
    fun `rootsChanged handles removal of files from root`() {
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
        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.event.removed).hasSize(1)
        assertThat(paramsSlot.captured.event.removed[0].name).isEqualTo("folder2")
    }

    @Test
    fun `rootsChanged handles multiple simultaneous additions and removals`() {
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
        testScope.advanceUntilIdle()
        val paramsSlot = slot<DidChangeWorkspaceFoldersParams>()
        verify(exactly = 1) { mockWorkspaceService.didChangeWorkspaceFolders(capture(paramsSlot)) }
        assertThat(paramsSlot.captured.event.added).hasSize(1)
        assertThat(paramsSlot.captured.event.removed).hasSize(1)
        assertThat(paramsSlot.captured.event.added[0].name).isEqualTo("folder3")
        assertThat(paramsSlot.captured.event.removed[0].name).isEqualTo("folder2")
    }

    private fun createMockVirtualFile(
        uri: URI,
        fileName: String,
        isDirectory: Boolean = false,
        fileTypeName: String = "PLAIN_TEXT",
        modificationStamp: Long = 1L,
    ): VirtualFile {
        val nioPath = mockk<Path> {
            every { toUri() } returns uri
        }
        val mockFileType = mockk<FileType> {
            every { name } returns fileTypeName
        }
        return mockk<VirtualFile> {
            every { this@mockk.isDirectory } returns isDirectory
            every { toNioPath() } returns nioPath
            every { url } returns uri.path
            every { path } returns "${uri.path}/$fileName"
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
            every { this@mockk.inputStream } returns "content".byteInputStream()
            every { fileType } returns mockFileType
            every { this@mockk.modificationStamp } returns modificationStamp
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
        fileTypeName: String = "PLAIN_TEXT",
        modificationStamp: Long = 1L,
    ): VFilePropertyChangeEvent {
        val parent = createMockVirtualFile(URI("file:///testDir/"), "testDir", true)
        val newUri = URI("file:///testDir/$newName")
        val file = createMockVirtualFile(newUri, newName, isDirectory, fileTypeName, modificationStamp)
        every { file.parent } returns parent

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
