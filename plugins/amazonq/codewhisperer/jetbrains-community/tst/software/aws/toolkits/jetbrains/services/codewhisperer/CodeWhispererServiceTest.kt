// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.Chunk
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SupplementalContextResult
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroup
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroupSettings
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CrossFileStrategy
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererTriggerType

class CodeWhispererServiceTest {
    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private lateinit var sut: CodeWhispererService
    private lateinit var userGroupSetting: CodeWhispererUserGroupSettings
    private lateinit var customizationConfig: CodeWhispererModelConfigurator
    private lateinit var file: PsiFile

    @Before
    fun setUp() {
        sut = CodeWhispererService.getInstance()
        userGroupSetting = mock {
            on { getUserGroup() } doReturn CodeWhispererUserGroup.Control
        }
        customizationConfig = mock {
            on { activeCustomization(any()) } doReturn CodeWhispererCustomization(
                "fake-arn",
                "fake-name",
                ""
            )
        }

        file = projectRule.fixture.addFileToProject(
            "Main.java",
            """
                  public class Main {
                      public static void main
            """.trimIndent()
        )
        runInEdtAndWait {
            projectRule.fixture.openFileInEditor(file.virtualFile)
        }

        ApplicationManager.getApplication().replaceService(CodeWhispererModelConfigurator::class.java, customizationConfig, disposableRule.disposable)
        ApplicationManager.getApplication().replaceService(CodeWhispererUserGroupSettings::class.java, userGroupSetting, disposableRule.disposable)
    }

    @Test
    fun `getRequestContext should collect file context, supplemental context and customization`() = runTest {
        val crossfileCandidate = projectRule.fixture.addFileToProject("Util.java", "public class Util {}")

        runInEdtAndWait {
            projectRule.fixture.openFileInEditor(crossfileCandidate.virtualFile)
            projectRule.fixture.openFileInEditor(file.virtualFile)
        }

        runInEdtAndWait {
            projectRule.fixture.editor.caretModel.moveToOffset(1)
        }

        val actual = sut.getRequestContext(
            triggerTypeInfo = TriggerTypeInfo(CodewhispererTriggerType.AutoTrigger, CodeWhispererAutomatedTriggerType.Classifier()),
            editor = projectRule.fixture.editor,
            project = projectRule.project,
            psiFile = file,
            latencyContext = LatencyContext()
        )

        assertThat(actual.customizationArn).isEqualTo("fake-arn")
        assertThat(actual.fileContextInfo.filename).isEqualTo("Main.java")
        assertThat(actual.fileContextInfo.programmingLanguage).isInstanceOf(CodeWhispererJava::class.java)
        assertThat(actual.fileContextInfo.caretContext).isEqualTo(
            CaretContext(
                leftFileContext = "p",
                rightFileContext = """
                  ublic class Main {
                      public static void main
                """.trimIndent(),
                "p"
            )
        )

        val supplementalContext = actual.supplementalContext
        assertThat(supplementalContext).isInstanceOf(SupplementalContextResult.Success::class.java)
        supplementalContext as SupplementalContextResult.Success
        assertThat(supplementalContext.contents).isNotEmpty
        assertThat(supplementalContext.contents.first()).isEqualTo(Chunk(content = "public class Util {}", path = "Util.java"))
        assertThat(supplementalContext.contentLength).isGreaterThan(0)
        assertThat(supplementalContext.strategy).isEqualTo(CrossFileStrategy.OpenTabsBM25)
    }

    @Test
    fun `getRequestContext should still collect file context and customizatioArn even if supplemental context fetching fails`() = runTest {
        val mockFileContextProvider = mock<FileContextProvider> {
            on { this.extractFileContext(any(), any()) } doReturn aFileContextInfo()
            onBlocking { this.extractSupplementalFileContext(any(), any()) } doThrow TimeoutCancellationException::class
        }

        projectRule.project.replaceService(FileContextProvider::class.java, mockFileContextProvider, disposableRule.disposable)
        ApplicationManager.getApplication().replaceService(CodeWhispererModelConfigurator::class.java, customizationConfig, disposableRule.disposable)

        val actual = sut.getRequestContext(
            TriggerTypeInfo(CodewhispererTriggerType.OnDemand, CodeWhispererAutomatedTriggerType.Unknown()),
            projectRule.fixture.editor,
            projectRule.project,
            file,
            LatencyContext()
        )

        assertThat(actual.customizationArn).isEqualTo("fake-arn")
        actual.supplementalContext.let {
            it as SupplementalContextResult.Failure
            assertThat(it.error).isInstanceOf(TimeoutCancellationException::class.java)
            assertThat(it.isTimeoutFailure()).isTrue
        }
    }

    @Test
    fun buildGenerateCompletionRequest() {
        val fileContext = FileContextInfo(
            CaretContext(
                leftFileContext = "left",
                rightFileContext = "right",
                leftContextOnCurrentLine = "l"
            ),
            filename = "Main.java",
            programmingLanguage = CodeWhispererJava.INSTANCE
        )
        val supplementalContext = SupplementalContextResult.Success(
            isUtg = false,
            contents = listOf(
                Chunk(content = "foo", path = "Foo.java"),
                Chunk(content = "bar", path = "Bar.java"),
                Chunk(content = "baz", path = "Baz.java")
            ),
            strategy = CrossFileStrategy.OpenTabsBM25,
            targetFileName = "Main.java",
            latency = 20,
        )
        val customizationArn = CodeWhispererModelConfigurator.getInstance().activeCustomization(projectRule.project)?.arn
        CodeWhispererService.buildCodeWhispererRequest(fileContext, supplementalContext, customizationArn).let {
            assertThat(it.customizationArn()).isEqualTo("fake-arn")
            assertThat(it.supplementalContexts()).matches { supContext ->
                supContext.size == supplementalContext.contents.size &&
                    supContext[0].content() == "foo" &&
                    supContext[0].filePath() == "Foo.java" &&
                    supContext[1].content() == "bar" &&
                    supContext[1].filePath() == "Bar.java" &&
                    supContext[2].content() == "baz" &&
                    supContext[2].filePath() == "Baz.java"
            }
            assertThat(it.fileContext()).matches { fileContext ->
                fileContext.filename() == "Main.java" &&
                    fileContext.programmingLanguage().languageName() == CodeWhispererJava.INSTANCE.toCodeWhispererRuntimeLanguage().languageId &&
                    fileContext.leftFileContent() == "left" &&
                    fileContext.rightFileContent() == "right"
            }
        }
    }

    @Test
    fun `buildGenerateCompletionRequest when there is no customization arn and no supplemental context`() {
        val fileContext = FileContextInfo(
            CaretContext(
                leftFileContext = "left",
                rightFileContext = "right",
                leftContextOnCurrentLine = "l"
            ),
            filename = "Main.java",
            programmingLanguage = CodeWhispererJava.INSTANCE
        )
        val supplementalContext = SupplementalContextResult.Failure(
            isUtg = false,
            targetFileName = "Main.java",
            latency = 20,
            error = Exception("unknown error")
        )

        customizationConfig.stub { on { activeCustomization(any()) } doReturn null }
        val customizationArn = CodeWhispererModelConfigurator.getInstance().activeCustomization(projectRule.project)?.arn
        CodeWhispererService.buildCodeWhispererRequest(fileContext, supplementalContext, customizationArn).let {
            assertThat(it.customizationArn()).isNull()
            assertThat(it.supplementalContexts()).isEmpty()
            assertThat(it.fileContext()).matches { fileContext ->
                fileContext.filename() == "Main.java" &&
                    fileContext.programmingLanguage().languageName() == CodeWhispererJava.INSTANCE.toCodeWhispererRuntimeLanguage().languageId &&
                    fileContext.leftFileContent() == "left" &&
                    fileContext.rightFileContent() == "right"
            }
        }
    }

    @Ignore("need update language type since Java is fully supported")
    @Test
    fun `getRequestContext - cross file context should be empty for non-cross-file user group`() = runTest {
        val file = projectRule.fixture.addFileToProject("main.java", "public class Main {}")

        runInEdtAndWait {
            projectRule.fixture.openFileInEditor(file.virtualFile)
        }

        val actual = sut.getRequestContext(
            TriggerTypeInfo(CodewhispererTriggerType.OnDemand, CodeWhispererAutomatedTriggerType.Unknown()),
            projectRule.fixture.editor,
            projectRule.project,
            file,
            LatencyContext()
        )

        actual.supplementalContext.let {
            it as SupplementalContextResult.Success
            assertThat(it.contents).isEmpty()
            assertThat(it.contentLength).isEqualTo(0)
        }
    }
}
