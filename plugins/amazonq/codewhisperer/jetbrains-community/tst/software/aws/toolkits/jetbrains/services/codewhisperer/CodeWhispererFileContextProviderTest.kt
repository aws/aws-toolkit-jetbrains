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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.project.EncoderServer
import software.aws.toolkits.jetbrains.services.amazonq.project.InlineBm25Chunk
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextController
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextProvider
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
import kotlin.test.assertNotNull

class CodeWhispererFileContextProviderTest {
    @JvmField
    @Rule
    val projectRule = HeavyJavaCodeInsightTestFixtureRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    lateinit var sut: DefaultCodeWhispererFileContextProvider

    // dependencies
    lateinit var featureConfigService: CodeWhispererFeatureConfigService
    lateinit var mockProjectContext: ProjectContextController

    lateinit var fixture: JavaCodeInsightTestFixture
    lateinit var project: Project

    @Before
    fun setup() {
        fixture = projectRule.fixture
        project = projectRule.project

        sut = FileContextProvider.getInstance(project) as DefaultCodeWhispererFileContextProvider

        featureConfigService = mock()
        ApplicationManager.getApplication()
            .replaceService(
                CodeWhispererFeatureConfigService::class.java,
                featureConfigService,
                disposableRule.disposable
            )

        mockProjectContext = mock()
        project.replaceService(ProjectContextController::class.java, mockProjectContext, disposableRule.disposable)
    }

    @Test
    fun `extractSupplementalFileContext should timeout 50ms`() = runTest {
        featureConfigService.stub { on { getInlineCompletion() } doReturn false }
        sut = spy(sut)

        val files = NaiveSampleCase.setupFixture(fixture)
        val queryPsi = files[0]
        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)

        sut.stub {
            runBlocking {
                doAnswer {
                    runBlocking { delay(100) }
                    aSupplementalContextInfo()
                }.whenever(sut).fetchOpenTabsContext(any(), any(), any())
            }
        }

        val result = sut.extractSupplementalFileContext(queryPsi, mockFileContext, 50L)
        assertNotNull(result)
        assertThat(result.isProcessTimeout).isTrue
    }

    @Test
    fun `should only call and use openTabsContext if projectContext is disabled`() = runTest {
        featureConfigService.stub { on { getInlineCompletion() } doReturn false }
        sut = spy(sut)

        val files = NaiveSampleCase.setupFixture(fixture)
        val queryPsi = files[0]
        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)

        val result = sut.extractSupplementalFileContextForSrc(queryPsi, mockFileContext)

        verify(sut, times(0)).fetchProjectContext(any(), any(), any())
        verify(sut, times(1)).fetchOpenTabsContext(any(), any(), any())

        assertThat(result.isUtg).isFalse
        assertThat(result.strategy).isEqualTo(CrossFileStrategy.OpenTabsBM25)
        assertThat(result.contents).isNotEmpty
    }

    @Test
    fun `should call both and use openTabsContext if projectContext is empty when it's enabled`() = runTest {
        mockProjectContext.stub { onBlocking { queryInline(any(), any()) }.doReturn(emptyList()) }
        featureConfigService.stub { on { getInlineCompletion() } doReturn true }
        sut = spy(sut)

        val files = NaiveSampleCase.setupFixture(fixture)
        val queryPsi = files[0]
        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)

        val result = sut.extractSupplementalFileContextForSrc(queryPsi, mockFileContext)

        verify(sut, times(1)).fetchProjectContext(any(), any(), any())
        verify(sut, times(1)).fetchOpenTabsContext(any(), any(), any())

        assertThat(result.isUtg).isFalse
        assertThat(result.strategy).isEqualTo(CrossFileStrategy.OpenTabsBM25)
        assertThat(result.contents).isNotEmpty
    }

    // move to projectContextControllerTest
    @Test
    fun `projectContextController should return empty result if provider throws`() = runTest {
        mockConstruction(ProjectContextProvider::class.java).use { providerContext ->
            mockConstruction(EncoderServer::class.java).use { serverContext ->
                assertThat(providerContext.constructed()).hasSize(0)
                assertThat(serverContext.constructed()).hasSize(0)
                val controller = ProjectContextController(project, TestScope())
                assertThat(providerContext.constructed()).hasSize(1)
                assertThat(serverContext.constructed()).hasSize(1)

                whenever(providerContext.constructed()[0].queryInline(any(), any())).thenThrow(RuntimeException("mock exception"))

                val result = controller.queryInline("query", "filePath")
                assertThat(result).isEmpty()
            }
        }
    }

    @Test
    fun `should use project context if it is present`() = runTest {
        mockProjectContext.stub {
            runBlocking {
                doReturn(
                    listOf(
                        InlineBm25Chunk("project_context1", "path1", 0.0),
                        InlineBm25Chunk("project_context2", "path2", 0.0),
                        InlineBm25Chunk("project_context3", "path3", 0.0),
                    )
                ).whenever(it).queryInline(any(), any())
            }
        }
        featureConfigService.stub { on { getInlineCompletion() } doReturn true }
        sut = spy(sut)
        val files = NaiveSampleCase.setupFixture(fixture)
        val queryPsi = files[0]
        val mockFileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)

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

    @Test
    fun `extractSupplementalFileContext from src file should extract src`() = runTest {
        val files = NaiveSampleCase.setupFixture(fixture)
        val queryPsi = files[0]

        sut = spy(sut)

        val fileContext = readAction { sut.extractFileContext(fixture.editor, queryPsi) }
        val supplementalContext = sut.extractSupplementalFileContext(queryPsi, fileContext, timeout = 50)

        assertThat(supplementalContext?.contents)
            .isNotNull
            .isNotEmpty
            .hasSize(3)

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

private object NaiveSampleCase {
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

    fun setupFixture(fixture: JavaCodeInsightTestFixture): List<PsiFile> {
        val queryPsi = fixture.addFileToProject("Query.java", query)
        val file1Psi = fixture.addFileToProject("File1.java", file1)
        val file2Psi = fixture.addFileToProject("File2.java", file2)
        val file3Psi = fixture.addFileToProject("File3.java", file3)
        val file4Psi = fixture.addFileToProject("File4.java", file4)
        val file5Psi = fixture.addFileToProject("File5.java", file5)
        val file6Psi = fixture.addFileToProject("File6.java", file6)
        val file7Psi = fixture.addFileToProject("File7.java", file7)
        val file8Psi = fixture.addFileToProject("File8.java", file8)
        val file9Psi = fixture.addFileToProject("File9.java", file9)

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

        return listOf(queryPsi, file1Psi, file2Psi, file3Psi, file4Psi, file5Psi, file6Psi, file7Psi, file8Psi, file9Psi)
    }
}
