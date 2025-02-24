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
import kotlinx.coroutines.test.runTest
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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

    @Before
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
        coEvery {
            mockLspService.executeSync(any())
        } coAnswers {
            val func = firstArg<suspend (AmazonQLanguageServer) -> Unit>()
            func.invoke(mockLanguageServer)
        }

        // Mock workspace service
        every { mockLanguageServer.workspaceService } returns mockWorkspaceService
        every { mockWorkspaceService.didCreateFiles(any()) } returns Unit
        every { mockWorkspaceService.didDeleteFiles(any()) } returns Unit
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
    fun `test didCreateFiles with valid events`() = runTest {
        val uri = URI("file:///test/path")
        val event = createMockVFileEvent(uri, FileChangeType.Created)

        sut.after(listOf(event))

        val paramsSlot = slot<CreateFilesParams>()
        verify { mockWorkspaceService.didCreateFiles(capture(paramsSlot)) }
        assertEquals(uri.toString(), paramsSlot.captured.files[0].uri)
    }

    @Test
    fun `test didDeleteFiles with valid event`() = runTest {
        val uri = URI("file:///test/path")
        val deleteEvent = createMockVFileEvent(uri, FileChangeType.Deleted)

        // Act
        sut.after(listOf(deleteEvent))

        val paramsSlot = slot<DeleteFilesParams>()
        verify { mockWorkspaceService.didDeleteFiles(capture(paramsSlot)) }
        assertEquals(uri.toString(), paramsSlot.captured.files[0].uri)

        // Assert
    }

    @Test
    fun `test didChangeWatchedFiles with valid events`() = runTest {
        // Arrange
        val createURI = URI("file:///test/pathOfCreation")
        val deleteURI = URI("file:///test/pathOfDeletion")
        val changeURI = URI("file:///test/pathOfChange")

        val virtualFileCreate = createMockVFileEvent(createURI, FileChangeType.Created)
        val virtualFileDelete = createMockVFileEvent(deleteURI, FileChangeType.Deleted)
        val virtualFileChange = createMockVFileEvent(changeURI)

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

    private fun createMockVFileEvent(uri: URI, type: FileChangeType = FileChangeType.Changed): VFileEvent {
        val virtualFile = mockk<VirtualFile>()
        val nioPath = mockk<Path>()

        every { virtualFile.toNioPath() } returns nioPath
        every { nioPath.toUri() } returns uri

        return when (type) {
            FileChangeType.Deleted -> mockk<VFileDeleteEvent>()
            FileChangeType.Created -> mockk<VFileCreateEvent>()
            else -> mockk<VFileEvent>()
        }.apply {
            every { file } returns virtualFile
        }
    }
}
