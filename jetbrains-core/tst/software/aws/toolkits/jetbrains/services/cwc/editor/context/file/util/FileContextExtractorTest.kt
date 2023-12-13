import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.every
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
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
    private val mockFqnWebviewAdapter: FqnWebviewAdapter = mockk<FqnWebviewAdapter>(relaxed = true)
    private val mockProject: Project = mockk<Project>(relaxed = true)
    private val mockLanguageExtractor: LanguageExtractor = mockk<LanguageExtractor>(relaxed = true)
    
    private val mockEditor: Editor = mockk<Editor>(relaxed = true)

    /*
    private lateinit var fileEditorManager: FileEditorManager
    private lateinit var psiDocumentManager: PsiDocumentManager
     */

    private lateinit var fileContextExtractor : FileContextExtractor

    @Before
    fun setUp() {
        fileContextExtractor = FileContextExtractor(mockFqnWebviewAdapter, mockProject, mockLanguageExtractor)

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
        clearMocks(
            mockFqnWebviewAdapter,
            mockProject,
            mockLanguageExtractor,
        )
    }

    @Test
    fun `extract returns null when editor is null`() {

        // Override return null editor
        every { FileEditorManager.getInstance(any()).selectedTextEditor } returns null

        runBlocking {

            val fileContextExtractor = FileContextExtractor(mockFqnWebviewAdapter, mockProject, mockLanguageExtractor)

            val result = fileContextExtractor.extract()

            assertNull(result)
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
            every { PsiDocumentManager.getInstance(any()).getPsiFile(any()) } returns mockPsiFile;
            val testFilePath = "/path/to/file"
            every { mockPsiFile.virtualFile.path } returns testFilePath

            mockkObject(MatchPolicyExtractor)
            coEvery { MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(any(), any(), any(), any()) } returns null

        // Act
        val result = runBlocking {
            fileContextExtractor.extract()
        }

            // Assert
            assertNotNull(result)
            assertEquals(testFileLanguage, result?.fileLanguage)
            assertEquals(testFilePath, result?.filePath)

        coVerify { MatchPolicyExtractor.extractMatchPolicyFromCurrentFile(
            false,
            testFileLanguage,
            testFileText,
            mockFqnWebviewAdapter
        )}

        unmockkStatic(PsiDocumentManager::class)
    }

    /*

    override fun getTestDataPath(): String {
        return "path/to/your/test/data"
    }
     */
}
