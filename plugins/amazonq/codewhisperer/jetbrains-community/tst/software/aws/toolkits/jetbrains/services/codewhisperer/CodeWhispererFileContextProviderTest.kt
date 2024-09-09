// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCpp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCsharp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererGo
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJson
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererKotlin
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPlainText
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRuby
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTf
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererYaml
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SupplementalContextResult
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroup
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroupSettings
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.SUPPLEMENTAL_CONTEXT_TIMEOUT
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
    fun `crossfile configuration`() {
        val userGroupSetting = mock<CodeWhispererUserGroupSettings>()
        ApplicationManager.getApplication().replaceService(CodeWhispererUserGroupSettings::class.java, userGroupSetting, disposableRule.disposable)

        whenever(userGroupSetting.getUserGroup()).thenReturn(CodeWhispererUserGroup.Control)
        assertThat(CodeWhispererConstants.CrossFile.CHUNK_SIZE).isEqualTo(60)

        whenever(userGroupSetting.getUserGroup()).thenReturn(CodeWhispererUserGroup.CrossFile)
        assertThat(CodeWhispererConstants.CrossFile.CHUNK_SIZE).isEqualTo(60)
    }

    @Test
    fun `shouldFetchUtgContext - fully support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJava.INSTANCE, CodeWhispererUserGroup.CrossFile)).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJava.INSTANCE, CodeWhispererUserGroup.Control)).isTrue
    }

    @Test
    fun `shouldFetchUtgContext - partially support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererPython.INSTANCE, CodeWhispererUserGroup.CrossFile)).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererPython.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
    }

    @Test
    fun `shouldFetchUtgContext - no support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJavaScript.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJavaScript.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJsx.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererJsx.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTypeScript.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTypeScript.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTsx.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTsx.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererCsharp.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererCsharp.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererKotlin.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererKotlin.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererGo.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererGo.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTsx.INSTANCE, CodeWhispererUserGroup.Control)).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchUtgContext(CodeWhispererTsx.INSTANCE, CodeWhispererUserGroup.CrossFile)).isFalse
    }

    @Test
    fun `shouldFetchCrossfileContext - fully support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJava.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererPython.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJavaScript.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJsx.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererTypeScript.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererTsx.INSTANCE, mock())).isTrue
    }

    @Test
    fun `shouldFetchCrossfileContext - partially support should return true if feature flag is enabled`() {
        val mockFeatureConfigService = mock<CodeWhispererFeatureConfigService> {
            on { getCrossfileConfig() } doReturn true
        }
        ApplicationManager.getApplication().replaceService(CodeWhispererFeatureConfigService::class.java, mockFeatureConfigService, disposableRule.disposable)

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererPython.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererCsharp.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererKotlin.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererGo.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererCpp.INSTANCE, mock())).isTrue
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererRuby.INSTANCE, mock())).isTrue
    }

    @Test
    fun `shouldFetchCrossfileContext - partially support should return false if feature flag is disabled`() {
        val mockFeatureConfigService = mock<CodeWhispererFeatureConfigService> {
            on { getCrossfileConfig() } doReturn false
        }
        ApplicationManager.getApplication().replaceService(CodeWhispererFeatureConfigService::class.java, mockFeatureConfigService, disposableRule.disposable)

        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererCsharp.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererKotlin.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererGo.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererCpp.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererRuby.INSTANCE, mock())).isFalse
    }

    @Test
    fun `shouldFetchCrossfileContext - no support`() {
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererYaml.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererJson.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererPlainText.INSTANCE, mock())).isFalse
        assertThat(DefaultCodeWhispererFileContextProvider.shouldFetchCrossfileContext(CodeWhispererTf.INSTANCE, mock())).isFalse
    }

    @Test
    fun `languages not supported will return empty directly`() {
        val psiFiles = setupFixture(fixture)
        val psi = psiFiles[0]

        runTest {
            var context = aFileContextInfo(CodeWhispererYaml.INSTANCE)
            assertThat(sut.extractSupplementalFileContextForSrc(psi, context)).isInstanceOf(SupplementalContextResult.NotSupported::class.java)
            assertThat(sut.extractSupplementalFileContextForTst(psi, context)).isInstanceOf(SupplementalContextResult.NotSupported::class.java)

            context = aFileContextInfo(CodeWhispererTf.INSTANCE)
            assertThat(sut.extractSupplementalFileContextForSrc(psi, context)).isInstanceOf(SupplementalContextResult.NotSupported::class.java)
            assertThat(sut.extractSupplementalFileContextForTst(psi, context)).isInstanceOf(SupplementalContextResult.NotSupported::class.java)
        }
    }

    @Test
    fun `use crossfile if UTG is not supported`() {
        sut = spy(sut)
        val psiFile1 = fixture.addFileToProject("main.ts", "class Main {}")
        val psiFile2 = fixture.addFileToProject("/foo/fooClass.ts", "export class Foo {}")
        val psiFile3 = fixture.addFileToProject("/bar/barClass.ts", "export class Bar {}")
        val testPsiFile = fixture.addFileToProject(
            "test/main.test.ts",
            """
            describe('test main', function () {
                it('should work', function () {
                    const foo = new Foo();
                    const bar = new Bar();
                });
            });
            """.trimIndent()
        )

        runInEdtAndWait {
            fixture.openFileInEditor(psiFile1.virtualFile)
            fixture.openFileInEditor(psiFile2.virtualFile)
            fixture.openFileInEditor(psiFile3.virtualFile)
            fixture.openFileInEditor(testPsiFile.virtualFile)
        }

        runTest {
            sut.extractSupplementalFileContext(testPsiFile, aFileContextInfo(CodeWhispererTypeScript.INSTANCE), SUPPLEMENTAL_CONTEXT_TIMEOUT)
            verify(sut).extractSupplementalFileContextForSrc(any(), any())
            verify(sut, times(0)).extractSupplementalFileContextForTst(any(), any())
        }
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
        val files = listOf(virtualFiles[1], virtualFiles[2])
        val result = sut.extractCodeChunksFromFiles(files)

        assertThat(result[0].content).isEqualTo(
            """public class UtilClass {
            |    public static int util() {};
            |    public static String util2() {};
            """.trimMargin()
        )

        assertThat(result[1].content).isEqualTo(
            """public class UtilClass {
            |    public static int util() {};
            |    public static String util2() {};
            |    private static void helper() {};
            |    public static final int constant1;
            |    public static final int constant2;
            |    public static final int constant3;
            |}
            """.trimMargin()
        )

        assertThat(result[2].content).isEqualTo(
            """public class MyController {
            |    @Get
            |    public Response getRecommendation(Request: req) {}
            """.trimMargin()
        )

        assertThat(result[3].content).isEqualTo(
            """public class MyController {
            |    @Get
            |    public Response getRecommendation(Request: req) {}            
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
    @Test
    fun `extractSupplementalFileContext should return successful result if there are files opened`() = runTest {
        withContext(getCoroutineBgContext()) {
            val psiFiles = setupFixture(fixture)
            sut = spy(sut)

            runInEdtAndWait {
                // simulate user opening files
                fixture.openFileInEditor(psiFiles[1].virtualFile)
                fixture.openFileInEditor(psiFiles[2].virtualFile)

                // current active editor
                fixture.openFileInEditor(psiFiles[0].virtualFile)
            }

            val fileContext = runReadAction { sut.extractFileContext(fixture.editor, psiFiles[0]) }
            val supplementalContext = runReadAction {
                async {
                    sut.extractSupplementalFileContext(psiFiles[0], fileContext, SUPPLEMENTAL_CONTEXT_TIMEOUT)
                }
            }.await()

            assertThat(supplementalContext).isInstanceOf(SupplementalContextResult.Success::class.java)
            supplementalContext as SupplementalContextResult.Success
            assertThat(supplementalContext.contentLength).isGreaterThan(0)
            assertThat(supplementalContext.isUtg).isFalse
            assertThat(supplementalContext.strategy).isEqualTo(CrossFileStrategy.OpenTabsBM25)
            assertThat(supplementalContext.targetFileName).isEqualTo(psiFiles[0].name)
            verify(sut).extractSupplementalFileContextForSrc(any(), any())
            verify(sut, times(0)).extractSupplementalFileContextForTst(any(), any())
        }
    }

    @Test
    fun `extractSupplementalFileContext should return failure result if there is no file opened`() = runTest {
        withContext(getCoroutineBgContext()) {
            val psiFiles = setupFixture(fixture)

            runInEdtAndWait {
                // current active editor
                fixture.openFileInEditor(psiFiles[0].virtualFile)
            }

            sut = spy(sut)

            val fileContext = runReadAction { sut.extractFileContext(fixture.editor, psiFiles[0]) }
            val supplementalContext = runReadAction {
                async {
                    sut.extractSupplementalFileContext(psiFiles[0], fileContext, SUPPLEMENTAL_CONTEXT_TIMEOUT)
                }
            }.await()

            assertThat(supplementalContext).isInstanceOf(SupplementalContextResult.Failure::class.java)
            supplementalContext as SupplementalContextResult.Failure
            assertThat(supplementalContext.error.message).matches {
                it.contains("No code chunk was found from crossfile candidates")
            }
            assertThat(supplementalContext.isUtg).isFalse
            assertThat(supplementalContext.targetFileName).isEqualTo(psiFiles[0].name)
            verify(sut).extractSupplementalFileContextForSrc(any(), any())
            verify(sut, times(0)).extractSupplementalFileContextForTst(any(), any())
        }
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
    fun `extractSupplementalFileContext from tst file should extract focal file`() {
        ApplicationManager.getApplication().replaceService(
            CodeWhispererUserGroupSettings::class.java,
            mock { on { getUserGroup() } doReturn CodeWhispererUserGroup.CrossFile },
            disposableRule.disposable
        )
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

        runReadAction {
            val fileContext = aFileContextInfo(CodeWhispererJava.INSTANCE)
            val supplementalContext = runBlocking {
                sut.extractSupplementalFileContext(tstFile, fileContext, 50)
            }
            assertThat(supplementalContext)
                .isInstanceOf(SupplementalContextResult.Success::class.java)
                .matches {
                    it as SupplementalContextResult.Success
                    it.contents.size == 1
                }

            assertThat(supplementalContext)
                .isInstanceOf(SupplementalContextResult.Success::class.java)
                .matches {
                    it as SupplementalContextResult.Success
                    it.contents[0].content == "UTG\n$JAVA_MAIN"
                }
        }

        runBlocking {
            verify(sut, times(0)).extractSupplementalFileContextForSrc(any(), any())
            verify(sut).extractSupplementalFileContextForTst(any(), any())
        }
    }

    private fun setupFixture(fixture: JavaCodeInsightTestFixture): List<PsiFile> {
        val psiFile1 = fixture.addFileToProject("Main.java", JAVA_MAIN)
        val psiFile2 = fixture.addFileToProject("UtilClass.java", JAVA_UTILCLASS)
        val psiFile3 = fixture.addFileToProject("controllers/MyController.java", JAVA_MY_CROLLTER)
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
            |    public static void main() {
            |        System.out.println("Hello world");               
            |    }
            |}
        """.trimMargin()

        private val JAVA_UTILCLASS = """public class UtilClass {
            |    public static int util() {};
            |    public static String util2() {};
            |    private static void helper() {};
            |    public static final int constant1;
            |    public static final int constant2;
            |    public static final int constant3;
            |}
        """.trimMargin()

        private val JAVA_MY_CROLLTER = """public class MyController {
            |    @Get
            |    public Response getRecommendation(Request: req) {}            
            |}
        """.trimMargin()
    }
}
