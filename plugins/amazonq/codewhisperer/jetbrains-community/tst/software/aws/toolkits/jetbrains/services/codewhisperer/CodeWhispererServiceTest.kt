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
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SupplementalContextResult
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroup
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroupSettings
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
        customizationConfig = mock() {
            on { activeCustomization(any()) } doReturn CodeWhispererCustomization(
                "fake-arn",
                "fake-name",
                ""
            )
        }

        file = projectRule.fixture.addFileToProject(
            "Main.java", """
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
    fun getRequestContext() = runInEdtAndWait {
        val crossfileCandidate = projectRule.fixture.addFileToProject("Util.java", "public class Util {}")
        println(file.virtualFile)
        println(crossfileCandidate.virtualFile)
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

        val supplementalContext = actual.supplementalContext()
        assertThat(supplementalContext).isInstanceOf(SupplementalContextResult.Success::class.java)
    }

    @Test
    fun `getRequestContext should have supplementalContext and customizatioArn if they're present`() {
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
        actual.supplementalContext().let {
            it as SupplementalContextResult.Failure
            assertThat(it.error).isInstanceOf(TimeoutCancellationException::class.java)
            assertThat(it.isTimeoutFailure()).isTrue
        }
    }

    @Ignore("need update language type since Java is fully supported")
    @Test
    fun `getRequestContext - cross file context should be empty for non-cross-file user group`() {
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

        actual.supplementalContext().let {
            it as SupplementalContextResult.Success
            assertThat(it.contents).isEmpty()
            assertThat(it.contentLength).isEqualTo(0)
        }
    }
}
