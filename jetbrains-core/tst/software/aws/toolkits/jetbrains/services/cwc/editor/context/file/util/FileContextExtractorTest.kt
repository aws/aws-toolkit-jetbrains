// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.FileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util.LanguageExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util.MatchPolicyExtractor
import software.aws.toolkits.jetbrains.services.cwc.utility.EdtUtility

class FileContextExtractorTest {

    // Constructor parameters
    private val mockFqnWebviewAdapter = mockk<FqnWebviewAdapter>(relaxed = true)
    private val mockProject = mockk<Project>(relaxed = true)
    private val mockLanguageExtractor = mockk<LanguageExtractor>(relaxed = true)

    private val mockEditor = mockk<Editor>(relaxed = true)

    private val fileContextExtractor = FileContextExtractor(mockFqnWebviewAdapter, mockProject, mockLanguageExtractor)

    @Before
    fun setUp() {
        // Editor
        mockkStatic(FileEditorManager::class)
        every { FileEditorManager.getInstance(any()).selectedTextEditor } returns mockEditor

        // computeInEdt
        mockkObject(EdtUtility)
        every { EdtUtility.runInEdt(any()) } answers {
            firstArg<() -> Unit>().invoke()
        }
        every { EdtUtility.runReadAction<String> (any()) } answers {
            firstArg<() -> String>().invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `extract returns null when editor is null`() {
        // Override return null editor
        every { FileEditorManager.getInstance(any()).selectedTextEditor } returns null

        runBlocking {
            val fileContextExtractor = FileContextExtractor(mockFqnWebviewAdapter, mockProject, mockLanguageExtractor)

            val result = fileContextExtractor.extract()

            assertThat(result).isNull()
        }
    }

    @Test
    fun `extract returns FileContext when editor is not null`() {
        val testFileLanguage = "java"
        every { mockLanguageExtractor.extractLanguageNameFromCurrentFile(any(), any()) } returns testFileLanguage

        val testFileText = "public class Test {}"
        every { mockEditor.document.text } returns testFileText

        mockkStatic(PsiDocumentManager::class)
        val mockPsiFile = mockk<PsiFile>(relaxed = true)
        every { PsiDocumentManager.getInstance(any()).getPsiFile(any()) } returns mockPsiFile
        val testFilePath = "/path/to/file"
        every { mockPsiFile.virtualFile.path } returns testFilePath

        mockkObject(MatchPolicyExtractor)
        coEvery { MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(any(), any(), any(), any()) } returns null

        // Act
        val result = runBlocking {
            fileContextExtractor.extract()
        }

        // Assert
        assertThat(result?.fileLanguage).isEqualTo(testFileLanguage)
        assertThat(result?.filePath).isEqualTo(testFilePath)

        coVerify {
            MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(
                false,
                testFileLanguage,
                testFileText,
                mockFqnWebviewAdapter
            )
        }

        unmockkStatic(PsiDocumentManager::class)
    }
}
