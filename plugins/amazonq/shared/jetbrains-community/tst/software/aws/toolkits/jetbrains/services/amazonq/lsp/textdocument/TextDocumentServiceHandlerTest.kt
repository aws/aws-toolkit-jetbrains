// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
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
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.services.TextDocumentService
import org.junit.Before
import org.junit.Test
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.FileUriUtil
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

class TextDocumentServiceHandlerTest {
    private lateinit var project: Project
    private lateinit var mockFileEditorManager: FileEditorManager
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockTextDocumentService: TextDocumentService
    private lateinit var sut: TextDocumentServiceHandler
    private lateinit var mockApplication: Application

    @Before
    fun setup() {
        project = mockk<Project>()
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
        every {
            mockLspService.executeSync<CompletableFuture<ResponseMessage>>(any())
        } coAnswers {
            val func = firstArg<suspend AmazonQLspService.(AmazonQLanguageServer) -> CompletableFuture<ResponseMessage>>()
            func.invoke(mockLspService, mockLanguageServer)
        }

        // Mock workspace service
        every { mockLanguageServer.textDocumentService } returns mockTextDocumentService
        every { mockTextDocumentService.didChange(any()) } returns Unit
        every { mockTextDocumentService.didSave(any()) } returns Unit
        every { mockTextDocumentService.didOpen(any()) } returns Unit
        every { mockTextDocumentService.didClose(any()) } returns Unit

        // Mock message bus
        val messageBus = mockk<MessageBus>()
        every { project.messageBus } returns messageBus
        val mockConnection = mockk<MessageBusConnection>()
        every { messageBus.connect(any<Disposable>()) } returns mockConnection
        every { mockConnection.subscribe(any(), any()) } just runs

        // Mock FileEditorManager
        mockFileEditorManager = mockk<FileEditorManager>()
        every { mockFileEditorManager.openFiles } returns emptyArray()
        every { project.getService(FileEditorManager::class.java) } returns mockFileEditorManager

        sut = TextDocumentServiceHandler(project, mockk())
    }

    @Test
    fun `didSave runs on beforeDocumentSaving`() = runTest {
        // Create test document and file
        val uri = URI.create("file:///test/path/file.txt")
        val document = mockk<Document> {
            every { text } returns "test content"
        }

        val file = createMockVirtualFile(uri)

        // Mock FileDocumentManager
        val fileDocumentManager = mockk<FileDocumentManager> {
            every { getFile(document) } returns file
        }

        // Replace the FileDocumentManager instance
        mockkStatic(FileDocumentManager::class) {
            every { FileDocumentManager.getInstance() } returns fileDocumentManager

            // Call the handler method
            sut.beforeDocumentSaving(document)

            // Verify the correct LSP method was called with matching parameters
            val paramsSlot = slot<DidSaveTextDocumentParams>()
            verify { mockTextDocumentService.didSave(capture(paramsSlot)) }

            with(paramsSlot.captured) {
                assertThat(textDocument.uri).isEqualTo(normalizeFileUri(uri.toString()))
                assertThat(text).isEqualTo("test content")
            }
        }
    }

    @Test
    fun `didOpen runs on service init`() = runTest {
        val uri = URI.create("file:///test/path/file.txt")
        val content = "test content"
        val file = createMockVirtualFile(uri, content)

        every { mockFileEditorManager.openFiles } returns arrayOf(file)

        sut = TextDocumentServiceHandler(project, mockk())

        val paramsSlot = slot<DidOpenTextDocumentParams>()
        verify { mockTextDocumentService.didOpen(capture(paramsSlot)) }

        with(paramsSlot.captured.textDocument) {
            assertThat(this.uri).isEqualTo(normalizeFileUri(uri.toString()))
            assertThat(text).isEqualTo(content)
            assertThat(languageId).isEqualTo("java")
            assertThat(version).isEqualTo(1)
        }
    }

    @Test
    fun `didOpen runs on fileOpened`() = runTest {
        val uri = URI.create("file:///test/path/file.txt")
        val content = "test content"
        val file = createMockVirtualFile(uri, content)

        sut.fileOpened(mockk(), file)

        val paramsSlot = slot<DidOpenTextDocumentParams>()
        verify { mockTextDocumentService.didOpen(capture(paramsSlot)) }

        with(paramsSlot.captured.textDocument) {
            assertThat(this.uri).isEqualTo(normalizeFileUri(uri.toString()))
            assertThat(text).isEqualTo(content)
            assertThat(languageId).isEqualTo("java")
            assertThat(version).isEqualTo(1)
        }
    }

    @Test
    fun `didClose runs on fileClosed`() = runTest {
        val uri = URI.create("file:///test/path/file.txt")
        val file = createMockVirtualFile(uri)

        sut.fileClosed(mockk(), file)

        val paramsSlot = slot<DidCloseTextDocumentParams>()
        verify { mockTextDocumentService.didClose(capture(paramsSlot)) }

        assertThat(paramsSlot.captured.textDocument.uri).isEqualTo(normalizeFileUri(uri.toString()))
    }

    @Test
    fun `didChange runs on content change events`() = runTest {
        val uri = URI.create("file:///test/path/file.txt")
        val document = mockk<Document> {
            every { text } returns "changed content"
            every { modificationStamp } returns 123L
        }

        val file = createMockVirtualFile(uri)

        val changeEvent = mockk<VFileContentChangeEvent> {
            every { this@mockk.file } returns file
        }

        // Mock FileDocumentManager
        val fileDocumentManager = mockk<FileDocumentManager> {
            every { getCachedDocument(file) } returns document
        }

        mockkStatic(FileDocumentManager::class) {
            every { FileDocumentManager.getInstance() } returns fileDocumentManager

            // Call the handler method
            sut.after(mutableListOf(changeEvent))
        }

        // Verify the correct LSP method was called with matching parameters
        val paramsSlot = slot<DidChangeTextDocumentParams>()
        verify { mockTextDocumentService.didChange(capture(paramsSlot)) }

        with(paramsSlot.captured) {
            assertThat(textDocument.uri).isEqualTo(normalizeFileUri(uri.toString()))
            assertThat(textDocument.version).isEqualTo(123)
            assertThat(contentChanges[0].text).isEqualTo("changed content")
        }
    }

    @Test
    fun `didSave does not run when URI is empty`() = runTest {
        val document = mockk<Document>()
        val file = createMockVirtualFile(URI.create(""))

        mockkObject(FileUriUtil) {
            every { FileUriUtil.toUriString(file) } returns null

            val fileDocumentManager = mockk<FileDocumentManager> {
                every { getFile(document) } returns file
            }

            mockkStatic(FileDocumentManager::class) {
                every { FileDocumentManager.getInstance() } returns fileDocumentManager

                sut.beforeDocumentSaving(document)

                verify(exactly = 0) { mockTextDocumentService.didSave(any()) }
            }
        }
    }

    @Test
    fun `didSave does not run when file is null`() = runTest {
        val document = mockk<Document>()

        val fileDocumentManager = mockk<FileDocumentManager> {
            every { getFile(document) } returns null
        }

        mockkStatic(FileDocumentManager::class) {
            every { FileDocumentManager.getInstance() } returns fileDocumentManager

            sut.beforeDocumentSaving(document)

            verify(exactly = 0) { mockTextDocumentService.didSave(any()) }
        }
    }

    @Test
    fun `didChange ignores non-content change events`() = runTest {
        val nonContentEvent = mockk<VFileEvent>() // Some other type of VFileEvent

        sut.after(mutableListOf(nonContentEvent))

        verify(exactly = 0) { mockTextDocumentService.didChange(any()) }
    }

    @Test
    fun `didChange skips files without cached documents`() = runTest {
        val uri = URI.create("file:///test/path/file.txt")
        val path = mockk<Path> {
            every { toUri() } returns uri
        }
        val file = mockk<VirtualFile> {
            every { toNioPath() } returns path
        }
        val changeEvent = mockk<VFileContentChangeEvent> {
            every { this@mockk.file } returns file
        }

        val fileDocumentManager = mockk<FileDocumentManager> {
            every { getCachedDocument(file) } returns null
        }

        mockkStatic(FileDocumentManager::class) {
            every { FileDocumentManager.getInstance() } returns fileDocumentManager

            sut.after(mutableListOf(changeEvent))

            verify(exactly = 0) { mockTextDocumentService.didChange(any()) }
        }
    }

    private fun createMockVirtualFile(
        uri: URI,
        content: String = "",
        fileTypeName: String = "JAVA",
        modificationStamp: Long = 1L,
    ): VirtualFile {
        val path = mockk<Path> {
            every { toUri() } returns uri
        }
        val inputStream = content.byteInputStream()

        val mockFileType = mockk<FileType> {
            every { name } returns fileTypeName
        }

        return mockk<VirtualFile> {
            every { url } returns uri.path
            every { toNioPath() } returns path
            every { isDirectory } returns false
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
            every { this@mockk.inputStream } returns inputStream
            every { fileType } returns mockFileType
            every { this@mockk.modificationStamp } returns modificationStamp
        }
    }

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
