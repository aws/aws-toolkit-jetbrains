// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.services.TextDocumentService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil
import software.aws.toolkits.jetbrains.utils.rules.CodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.satisfiesKt
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.collections.first

class TextDocumentServiceHandlerTest {
    private lateinit var mockLanguageServer: AmazonQLanguageServer
    private lateinit var mockTextDocumentService: TextDocumentService
    private lateinit var sut: TextDocumentServiceHandler

    @get:Rule
    val projectRule = object : CodeInsightTestFixtureRule() {
        override fun createTestFixture(): CodeInsightTestFixture {
            val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
            val fixtureBuilder = fixtureFactory.createLightFixtureBuilder(testDescription, testName)
            val newFixture = fixtureFactory
                .createCodeInsightFixture(fixtureBuilder.fixture, fixtureFactory.createTempDirTestFixture())
            newFixture.setUp()
            newFixture.testDataPath = testDataPath

            return newFixture
        }
    }

    @get:Rule
    val disposableRule = DisposableRule()

    @get:Rule
    val testName = TestName()

    @Before
    fun setup() {
        mockTextDocumentService = mockk<TextDocumentService>()
        mockLanguageServer = mockk<AmazonQLanguageServer>()

        // Mock the LSP service
        val mockLspService = mockk<AmazonQLspService>(relaxed = true)

        // Mock the service methods on Project
        projectRule.project.replaceService(AmazonQLspService::class.java, mockLspService, disposableRule.disposable)

        // Mock the LSP service's executeSync method as a suspend function
        coEvery {
            mockLspService.executeIfRunning<CompletableFuture<ResponseMessage>>(any())
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
    }

    @After
    fun tearDown() {
        try {
            Disposer.dispose(sut)
        } catch (_: Exception) {
        }
    }

    @Test
    fun `didSave runs on beforeDocumentSaving`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)

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
            advanceUntilIdle()
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
        val content = "test content"
        val file = withContext(EDT) {
            projectRule.fixture.createFile(testName.methodName, content).also { projectRule.fixture.openFileInEditor(it) }
        }
        advanceUntilIdle()
        sut = TextDocumentServiceHandler(projectRule.project, this)
        advanceUntilIdle()

        val paramsSlot = mutableListOf<DidOpenTextDocumentParams>()
        verify { mockTextDocumentService.didOpen(capture(paramsSlot)) }

        assertThat(paramsSlot.first().textDocument).satisfiesKt {
            assertThat(it.uri).isEqualTo(file.toNioPath().toUri().toString())
            assertThat(it.text).isEqualTo(content)
            assertThat(it.languageId).isEqualTo("plain_text")
        }
    }

    @Test
    fun `didOpen runs on fileOpened`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        advanceUntilIdle()
        val content = "test content"
        val file = withContext(EDT) {
            projectRule.fixture.createFile(testName.methodName, content).also { projectRule.fixture.openFileInEditor(it) }
        }
        advanceUntilIdle()

        val paramsSlot = mutableListOf<DidOpenTextDocumentParams>()
        verify { mockTextDocumentService.didOpen(capture(paramsSlot)) }

        assertThat(paramsSlot.first().textDocument).satisfiesKt {
            assertThat(it.uri).isEqualTo(file.toNioPath().toUri().toString())
            assertThat(it.text).isEqualTo(content)
            assertThat(it.languageId).isEqualTo("plain_text")
        }
    }

    @Test
    fun `fileOpened suppreses document read failure`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        advanceUntilIdle()

        val file = mockk<VirtualFile> {
            every { inputStream } throws RuntimeException("read failure")
            every { getUserData<Any>(any()) } returns null
            every { putUserData<Any>(any(), any()) } returns Unit
            every { isValid } returns false
        }
        sut.fileOpened(mockk(), file)

        verify(exactly = 0) { mockTextDocumentService.didOpen(any()) }
    }

    @Test
    fun `didClose runs on fileClosed`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        val file = withContext(EDT) {
            projectRule.fixture.createFile(testName.methodName, "").also {
                projectRule.fixture.openFileInEditor(it)
                FileEditorManagerEx.getInstanceEx(projectRule.project).closeAllFiles()
            }
        }

        advanceUntilIdle()
        val paramsSlot = slot<DidCloseTextDocumentParams>()
        verify { mockTextDocumentService.didClose(capture(paramsSlot)) }

        assertThat(paramsSlot.captured.textDocument.uri).isEqualTo(file.toNioPath().toUri().toString())
    }

    @Test
    fun `didChange runs on content change events`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        val file = withContext(EDT) {
            projectRule.fixture.createFile(testName.methodName, "").also {
                projectRule.fixture.openFileInEditor(it)

                writeAction {
                    it.writeText("changed content")
                }
            }
        }

        // Verify the correct LSP method was called with matching parameters
        advanceUntilIdle()
        val paramsSlot = mutableListOf<DidChangeTextDocumentParams>()
        verify { mockTextDocumentService.didChange(capture(paramsSlot)) }

        assertThat(paramsSlot.first()).satisfiesKt {
            assertThat(it.textDocument.uri).isEqualTo(file.toNioPath().toUri().toString())
            assertThat(it.contentChanges[0].text).isEqualTo("changed content")
        }
    }

    @Test
    fun `didSave does not run when URI is empty`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        val document = mockk<Document>()
        val file = createMockVirtualFile(URI.create(""))

        mockkObject(LspEditorUtil) {
            every { LspEditorUtil.toUriString(file) } returns null

            val fileDocumentManager = mockk<FileDocumentManager> {
                every { getFile(document) } returns file
            }

            mockkStatic(FileDocumentManager::class) {
                every { FileDocumentManager.getInstance() } returns fileDocumentManager

                sut.beforeDocumentSaving(document)

                advanceUntilIdle()
                verify(exactly = 0) { mockTextDocumentService.didSave(any()) }
            }
        }
    }

    @Test
    fun `didSave does not run when file is null`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        val document = mockk<Document>()

        val fileDocumentManager = mockk<FileDocumentManager> {
            every { getFile(document) } returns null
        }

        mockkStatic(FileDocumentManager::class) {
            every { FileDocumentManager.getInstance() } returns fileDocumentManager

            sut.beforeDocumentSaving(document)

            advanceUntilIdle()
            verify(exactly = 0) { mockTextDocumentService.didSave(any()) }
        }
    }

    @Test
    fun `didChange ignores non-content change events`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
        val nonContentEvent = mockk<VFileEvent>() // Some other type of VFileEvent

        sut.after(mutableListOf(nonContentEvent))

        advanceUntilIdle()
        verify(exactly = 0) { mockTextDocumentService.didChange(any()) }
    }

    @Test
    fun `didChange skips files without cached documents`() = runTest {
        sut = TextDocumentServiceHandler(projectRule.project, this)
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

            advanceUntilIdle()
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
            every { isBinary } returns false
        }

        return spyk<VirtualFile>(LightVirtualFile("test.java")) {
            every { url } returns uri.path
            every { toNioPath() } returns path
            every { isDirectory } returns false
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
            every { this@spyk.inputStream } returns inputStream
            every { fileType } returns mockFileType
            every { this@spyk.modificationStamp } returns modificationStamp
        }
    }

    private fun normalizeFileUri(uri: String): String {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return uri
        }

        if (!uri.startsWith("file:///")) {
            return uri
        }

        if (uri.startsWith("file://$windowsDrive:/")) {
            val path = uri.substringAfter("file://$windowsDrive:/")
            return "file:///$windowsDrive:/$path"
        }

        val path = uri.substringAfter("file:///")
        return "file:///$windowsDrive:/$path"
    }

    private val windowsDrive: String
        get() = java.nio.file.Paths.get("").toAbsolutePath().root
            ?.toString()?.firstOrNull()?.uppercaseChar()?.toString() ?: "C"
}
