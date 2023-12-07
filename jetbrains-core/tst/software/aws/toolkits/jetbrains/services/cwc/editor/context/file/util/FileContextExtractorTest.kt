import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import software.aws.toolkits.jetbrains.services.amazonq.webview.FqnWebviewAdapter
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.FileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util.LanguageExtractor

class FileContextExtractorTest : BasePlatformTestCase() {
    private lateinit var fqnWebviewAdapter: FqnWebviewAdapter
    private lateinit var project: Project
    private lateinit var languageExtractor: LanguageExtractor
    private lateinit var fileEditorManager: FileEditorManager
    private lateinit var psiDocumentManager: PsiDocumentManager

    @Before
    override fun setUp() {
        super.setUp()
        fqnWebviewAdapter = Mockito.mock(FqnWebviewAdapter::class.java)
        project = myFixture.project
        languageExtractor = LanguageExtractor()
        fileEditorManager = Mockito.mock(FileEditorManager::class.java)
        psiDocumentManager = Mockito.mock(PsiDocumentManager::class.java)
    }

    @Test
    fun `extract returns null when editor is null`() {
        runBlocking {
            Mockito.`when`(fileEditorManager.selectedTextEditor).thenReturn(null)
            val fileContextExtractor = FileContextExtractor(fqnWebviewAdapter, project, languageExtractor)

            val result = fileContextExtractor.extract()

            assertNull(result)
        }
    }

    // This is not working, we have to mock a lot of complex calls to the underlying system.
    // But it's failing with a generic test not found error ...
    @Test
    fun `extract returns FileContext when editor is not null`() {
        runBlocking {
            val editor = Mockito.mock(Editor::class.java)
            Mockito.`when`(fileEditorManager.selectedTextEditor).thenReturn(editor)
            Mockito.`when`(editor.document.text).thenReturn("public class Test {}")
            val importsString = "[\"java.util.List\", \"java.util.ArrayList\"]"
            Mockito.`when`(fqnWebviewAdapter.readImports(any())).thenReturn(importsString)
            val fileContextExtractor = FileContextExtractor(fqnWebviewAdapter, project, languageExtractor)

            var fakeFilePath = "/path/to/file"
            val doc = Mockito.mock(Document::class.java)
            val psiFile = Mockito.mock(PsiFile::class.java)
            val virtualFile = Mockito.mock(VirtualFile::class.java)
            Mockito.`when`(editor.document).thenReturn(doc)
            Mockito.`when`(psiDocumentManager.getPsiFile(doc)).thenReturn(psiFile)
            Mockito.`when`(psiFile.virtualFile).thenReturn(virtualFile)
            Mockito.`when`(virtualFile.path).thenReturn(fakeFilePath)

            // Act
            val result = fileContextExtractor.extract()

            // Assert
            assertNotNull(result)
            assertEquals("java", result?.fileLanguage)
            assertEquals(fakeFilePath, result?.filePath)
            assertNotNull(result?.matchPolicy)
        }
    }

    override fun getTestDataPath(): String {
        return "path/to/your/test/data"
    }
}
