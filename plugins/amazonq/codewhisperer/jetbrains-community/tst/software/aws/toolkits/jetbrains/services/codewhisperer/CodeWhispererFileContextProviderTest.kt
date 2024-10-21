// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.project.InlineBm25Chunk
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextController
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCpp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCsharp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererGo
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererKotlin
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRuby
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CrossFileStrategy
import software.aws.toolkits.jetbrains.services.codewhisperer.util.DefaultCodeWhispererFileContextProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.addClass
import software.aws.toolkits.jetbrains.utils.rules.addModule
import software.aws.toolkits.jetbrains.utils.rules.addTestClass

class CodeWhispererFileContextProviderTest {
    @JvmField
    @Rule
    val projectRule = HeavyJavaCodeInsightTestFixtureRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    lateinit var sut: DefaultCodeWhispererFileContextProvider

    lateinit var fixture: JavaCodeInsightTestFixture
    lateinit var project: Project

    @Before
    fun setup() {
        fixture = projectRule.fixture
        project = projectRule.project

        sut = FileContextProvider.getInstance(project) as DefaultCodeWhispererFileContextProvider
    }

    @Test
    fun `should use open tabs if project context is empty due to project context is disabled`() = runTest {
        sut = spy(sut)
        val queryPsi = fixture.addFileToProject("Query.java", SampleCase.query)
        val file1Psi = fixture.addFileToProject("File1.java", SampleCase.file1)
        val file2Psi = fixture.addFileToProject("File2.java", SampleCase.file2)
        val file3Psi = fixture.addFileToProject("File3.java", SampleCase.file3)
        val file4Psi = fixture.addFileToProject("File4.java", SampleCase.file4)
        val file5Psi = fixture.addFileToProject("File5.java", SampleCase.file5)
        val file6Psi = fixture.addFileToProject("File6.java", SampleCase.file6)
        val file7Psi = fixture.addFileToProject("File7.java", SampleCase.file7)
        val file8Psi = fixture.addFileToProject("File8.java", SampleCase.file8)
        val file9Psi = fixture.addFileToProject("File9.java", SampleCase.file9)

        runInEdtAndWait {
            fixture.openFileInEditor(file1Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file2Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file3Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file4Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file5Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file6Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file7Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file8Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file9Psi.viewProvider.virtualFile)
        }

        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)
        val mockFeatureConfig: CodeWhispererFeatureConfigService = mock { on { getInlineCompletion() } doReturn false }
        ApplicationManager.getApplication()
            .replaceService(CodeWhispererFeatureConfigService::class.java, mockFeatureConfig, disposableRule.disposable)

        val result = sut.extractSupplementalFileContextForSrc(queryPsi, mockFileContext)

        assertThat(result.isUtg).isFalse
        assertThat(result.strategy).isEqualTo(CrossFileStrategy.OpenTabsBM25)
        assertThat(result.contents).hasSize(3)
    }

    @Test
    fun `should use open tabs if project context is empty due to unknown error`() = runTest {
        sut = spy(sut)
        val queryPsi = fixture.addFileToProject("Query.java", SampleCase.query)
        val file1Psi = fixture.addFileToProject("File1.java", SampleCase.file1)
        val file2Psi = fixture.addFileToProject("File2.java", SampleCase.file2)
        val file3Psi = fixture.addFileToProject("File3.java", SampleCase.file3)
        val file4Psi = fixture.addFileToProject("File4.java", SampleCase.file4)
        val file5Psi = fixture.addFileToProject("File5.java", SampleCase.file5)
        val file6Psi = fixture.addFileToProject("File6.java", SampleCase.file6)
        val file7Psi = fixture.addFileToProject("File7.java", SampleCase.file7)
        val file8Psi = fixture.addFileToProject("File8.java", SampleCase.file8)
        val file9Psi = fixture.addFileToProject("File9.java", SampleCase.file9)

        runInEdtAndWait {
            fixture.openFileInEditor(file1Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file2Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file3Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file4Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file5Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file6Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file7Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file8Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file9Psi.viewProvider.virtualFile)
        }

        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)
        val mockFeatureConfig: CodeWhispererFeatureConfigService = mock { on { getInlineCompletion() } doReturn true }
        ApplicationManager.getApplication()
            .replaceService(CodeWhispererFeatureConfigService::class.java, mockFeatureConfig, disposableRule.disposable)

        val mockProjectContext = mock<ProjectContextController> {
            on { queryInline(any(), any()) } doReturn emptyList()
        }
        project.replaceService(ProjectContextController::class.java, mockProjectContext, disposableRule.disposable)

        val result = sut.extractSupplementalFileContextForSrc(queryPsi, mockFileContext)

        assertThat(result.isUtg).isFalse
        assertThat(result.strategy).isEqualTo(CrossFileStrategy.OpenTabsBM25)
        assertThat(result.contents).hasSize(3)
    }

    @Test
    fun `should use project context if it is present`() = runTest {
        sut = spy(sut)
        val queryPsi = fixture.addFileToProject("Query.java", SampleCase.query)
        val file1Psi = fixture.addFileToProject("File1.java", SampleCase.file1)
        val file2Psi = fixture.addFileToProject("File2.java", SampleCase.file2)
        val file3Psi = fixture.addFileToProject("File3.java", SampleCase.file3)
        val file4Psi = fixture.addFileToProject("File4.java", SampleCase.file4)
        val file5Psi = fixture.addFileToProject("File5.java", SampleCase.file5)
        val file6Psi = fixture.addFileToProject("File6.java", SampleCase.file6)
        val file7Psi = fixture.addFileToProject("File7.java", SampleCase.file7)
        val file8Psi = fixture.addFileToProject("File8.java", SampleCase.file8)
        val file9Psi = fixture.addFileToProject("File9.java", SampleCase.file9)

        runInEdtAndWait {
            fixture.openFileInEditor(file1Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file2Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file3Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file4Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file5Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file6Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file7Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file8Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file9Psi.viewProvider.virtualFile)
        }

        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)
        val mockFeatureConfig: CodeWhispererFeatureConfigService = mock { on { getInlineCompletion() } doReturn true }
        ApplicationManager.getApplication()
            .replaceService(CodeWhispererFeatureConfigService::class.java, mockFeatureConfig, disposableRule.disposable)

        val mockProjectContext = mock<ProjectContextController> {
            on {
                queryInline(any(), any())
            } doReturn listOf(
                InlineBm25Chunk("project_context1", "projectContext", 0.0),
                InlineBm25Chunk("project_context2", "projectContext", 0.0),
                InlineBm25Chunk("project_context3", "projectContext", 0.0),
            )
        }
        project.replaceService(ProjectContextController::class.java, mockProjectContext, disposableRule.disposable)

        val result = sut.extractSupplementalFileContextForSrc(queryPsi, mockFileContext)

        assertThat(result.isUtg).isFalse
        assertThat(result.strategy).isEqualTo(CrossFileStrategy.ProjectContext)
        assertThat(result.contents).hasSize(3)
    }

    @Test
    fun `crossfile configuration`() {
        assertThat(CodeWhispererConstants.CrossFile.CHUNK_SIZE).isEqualTo(60)
    }

    @Test
    fun `shouldFetchUtgContext - fully support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJava.INSTANCE)).isTrue
    }

    @Test
    fun `shouldFetchUtgContext - no support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererPython.INSTANCE)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJavaScript.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJsx.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTypeScript.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTsx.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererCsharp.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererKotlin.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererGo.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTsx.INSTANCE)).isNull()
    }

    @Test
    fun `shouldFetchCrossfileContext - fully support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJava.INSTANCE)).isTrue

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererPython.INSTANCE)).isTrue

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJavaScript.INSTANCE)).isTrue

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJsx.INSTANCE)).isTrue

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererTypeScript.INSTANCE)).isTrue

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererTsx.INSTANCE)).isTrue
    }

    @Test
    fun `shouldFetchCrossfileContext - no support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererCsharp.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererKotlin.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererGo.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererCpp.INSTANCE)).isNull()

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererRuby.INSTANCE)).isNull()
    }

    @Test
    fun `languages not supporting supplemental context will return empty`() = runTest {
        val psiFiles = setupFixture(fixture)
        val psi = psiFiles[0]

        var context = aFileContextInfo(CodeWhispererCsharp.INSTANCE)

        assertThat(sut.extractSupplementalFileContextForSrc(psi, context).contents).isEmpty()
        assertThat(sut.extractSupplementalFileContextForTst(psi, context).contents).isEmpty()

        context = aFileContextInfo(CodeWhispererKotlin.INSTANCE)
        assertThat(sut.extractSupplementalFileContextForSrc(psi, context).contents).isEmpty()
        assertThat(sut.extractSupplementalFileContextForTst(psi, context).contents).isEmpty()
    }

    @Test
    fun `extractFileContext should return correct strings`() {
        val src = """
            public class Main {
                public static void main() {
                    System.out.println("Hello world");
                }
            }
        """.trimIndent()
        val psiFile = fixture.configureByText("Main.java", src)

        val fileContext = runInEdtAndGet {
            fixture.editor.caretModel.moveToOffset(47)
            assertThat(fixture.editor.document.text.substring(0, 47)).isEqualTo(
                """
                  public class Main {
                      public static void main
                """.trimIndent()
            )

            assertThat(fixture.editor.document.text.substring(47)).isEqualTo(
                """
                    () {
                            System.out.println("Hello world");
                        }
                    }
                """.trimIndent()
            )

            sut.extractFileContext(fixture.editor, psiFile)
        }

        assertThat(fileContext.filename).isEqualTo("Main.java")
        assertThat(fileContext.programmingLanguage).isEqualTo(CodeWhispererJava.INSTANCE)
        assertThat(fileContext.caretContext.leftFileContext).isEqualTo(
            """
                public class Main {
                    public static void main
            """.trimIndent()
        )
        assertThat(fileContext.caretContext.rightFileContext).isEqualTo(
            """
                () {
                        System.out.println("Hello world");
                    }
                }
            """.trimIndent()
        )
        assertThat(fileContext.caretContext.leftContextOnCurrentLine).isEqualTo("    public static void main")
    }

    @Test
    fun `test extractCodeChunksFromFiles should read files from file producers to get 60 chunks`() = runTest {
        val psiFiles = setupFixture(fixture)
        val virtualFiles = psiFiles.mapNotNull { it.virtualFile }
        val javaMainPsiFile = psiFiles.first()

        val fileProducer1: suspend (PsiFile) -> List<VirtualFile> = { psiFile ->
            listOf(virtualFiles[1])
        }

        val fileProducer2: suspend (PsiFile) -> List<VirtualFile> = { psiFile ->
            listOf(virtualFiles[2])
        }

        val result = sut.extractCodeChunksFromFiles(javaMainPsiFile, listOf(fileProducer1, fileProducer2))

        assertThat(result[0].content).isEqualTo(
            """public class UtilClass {
            |    public static int util() {}
            |    public static String util2() {}
            """.trimMargin()
        )

        assertThat(result[1].content).isEqualTo(
            """public class UtilClass {
            |    public static int util() {}
            |    public static String util2() {}
            |    private static void helper() {}
            |    public static final int constant1;
            |    public static final int constant2;
            |    public static final int constant3;
            |}
            """.trimMargin()
        )

        assertThat(result[2].content).isEqualTo(
            """public class MyController {
            |    @Get
            |    public Response getRecommendation(Request req) {}
            """.trimMargin()
        )

        assertThat(result[3].content).isEqualTo(
            """public class MyController {
            |    @Get
            |    public Response getRecommendation(Request req) {}
            |}
            """.trimMargin()
        )
    }

    /**
     * - src/
     *     - java/
     *          - Main.java
     *          - Util.java
     *          - controllers/
     *              -MyApiController.java
     * - tst/
     *     - java/
     *          - MainTest.java
     *
     */
    // TODO: fix this test, in test env, psiFile.virtualFile == null @psiGist.getFileData(psiFile) { psiFile -> ... }
    @Test
    fun `extractSupplementalFileContext from src file should extract src`() = runTest {
        val queryPsi = fixture.addFileToProject("Query.java", SampleCase.query)
        val file1Psi = fixture.addFileToProject("File1.java", SampleCase.file1)
        val file2Psi = fixture.addFileToProject("File2.java", SampleCase.file2)
        val file3Psi = fixture.addFileToProject("File3.java", SampleCase.file3)
        val file4Psi = fixture.addFileToProject("File4.java", SampleCase.file4)
        val file5Psi = fixture.addFileToProject("File5.java", SampleCase.file5)
        val file6Psi = fixture.addFileToProject("File6.java", SampleCase.file6)
        val file7Psi = fixture.addFileToProject("File7.java", SampleCase.file7)
        val file8Psi = fixture.addFileToProject("File8.java", SampleCase.file8)
        val file9Psi = fixture.addFileToProject("File9.java", SampleCase.file9)

        runInEdtAndWait {
            fixture.openFileInEditor(file1Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file2Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file3Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file4Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file5Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file6Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file7Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file8Psi.viewProvider.virtualFile)
            fixture.openFileInEditor(file9Psi.viewProvider.virtualFile)
        }

        sut = spy(sut)

        val fileContext = readAction { sut.extractFileContext(fixture.editor, queryPsi) }
        val supplementalContext = sut.extractSupplementalFileContext(queryPsi, fileContext, timeout = 50)

        assertThat(supplementalContext?.contents)
            .isNotNull
            .isNotEmpty
        verify(sut).extractSupplementalFileContextForSrc(any(), any())
        verify(sut, times(0)).extractSupplementalFileContextForTst(any(), any())
    }

    /**
     * - src/
     *     - java/
     *          - Main.java
     *          - Util.java
     *          - controllers/
     *              -MyApiController.java
     * - tst/
     *     - java/
     *          - MainTest.java
     *
     */
    @Test
    fun `extractSupplementalFileContext from tst file should extract focal file`() = runTest {
        val module = fixture.addModule("main")
        fixture.addClass(module, JAVA_MAIN)

        val psiTestClass = fixture.addTestClass(
            module,
            """
            public class MainTest {}
            """
        )

        val tstFile = psiTestClass.containingFile

        sut = spy(sut)

        val fileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)
        val supplementalContext = sut.extractSupplementalFileContext(tstFile, fileContext, 50)

        assertThat(supplementalContext?.contents)
            .isNotNull
            .isNotEmpty
            .hasSize(1)

        assertThat(supplementalContext?.contents?.get(0)?.content)
            .isNotNull
            .isEqualTo("UTG\n$JAVA_MAIN")

        verify(sut, times(0)).extractSupplementalFileContextForSrc(any(), any())
        verify(sut).extractSupplementalFileContextForTst(any(), any())
    }

    private fun setupFixture(fixture: JavaCodeInsightTestFixture): List<PsiFile> {
        val psiFile1 = fixture.addFileToProject("Main.java", JAVA_MAIN)
        val psiFile2 = fixture.addFileToProject("UtilClass.java", JAVA_UTILCLASS)
        val psiFile3 = fixture.addFileToProject("controllers/MyController.java", JAVA_MYCONTROLLER)
        val psiFile4 = fixture.addFileToProject("helpers/Helper1.java", "Class Helper1 {}")
        val psiFile5 = fixture.addFileToProject("helpers/Helper2.java", "Class Helper2 {}")
        val psiFile6 = fixture.addFileToProject("helpers/Helper3.java", "Class Helper3 {}")
        val testPsiFile = fixture.addFileToProject(
            "test/java/MainTest.java",
            """
            public class MainTest {
                @Before
                public void setup() {}
            }
            """.trimIndent()
        )

        runInEdtAndWait {
            fixture.openFileInEditor(psiFile1.virtualFile)
            fixture.editor.caretModel.moveToOffset(fixture.editor.document.textLength)
        }

        return listOf(psiFile1, psiFile2, psiFile3, testPsiFile, psiFile4, psiFile5, psiFile6)
    }

    companion object {
        private val JAVA_MAIN = """public class Main {
            |    public static void main(String[] args) {
            |        System.out.println("Hello world");               
            |    }
            |}
        """.trimMargin()

        // language=Java
        private val JAVA_UTILCLASS = """public class UtilClass {
            |    public static int util() {}
            |    public static String util2() {}
            |    private static void helper() {}
            |    public static final int constant1;
            |    public static final int constant2;
            |    public static final int constant3;
            |}
        """.trimMargin()

        // language=Java
        private val JAVA_MYCONTROLLER = """public class MyController {
            |    @Get
            |    public Response getRecommendation(Request req) {}
            |}
        """.trimMargin()
    }
}

private object SampleCase {
    const val file1 = "Human machine interface for lab abc computer applications"
    const val file2 = "A survey of user opinion of computer system response time"
    const val file3 = "The EPS user interface management system"
    const val file4 = "System and human system engineering testing of EPS"
    const val file5 = "Relation of user perceived response time to error measurement"
    const val file6 = "The generation of random binary unordered trees"
    const val file7 = "The intersection graph of paths in trees"
    const val file8 = "Graph minors IV Widths of trees and well quasi ordering"
    const val file9 = "Graph minors A survey"
    const val query = "The intersection of graph survey and trees"
}
