// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionTriggerKind
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.aws.toolkits.telemetry.CodewhispererTriggerType

class CodeWhispererServiceTest : CodeWhispererTestBase() {

    private lateinit var customizationConfig: CodeWhispererModelConfigurator
    private lateinit var file: PsiFile

    @Before
    override fun setUp() {
        super.setUp()

        customizationConfig = mock()
        file = projectRule.fixture.addFileToProject("main.java", "public class Main {}")
        runInEdtAndWait {
            projectRule.fixture.openFileInEditor(file.virtualFile)
        }

        ApplicationManager.getApplication().replaceService(CodeWhispererModelConfigurator::class.java, customizationConfig, disposableRule.disposable)
    }

    @Test
    fun `getRequestContext should use correct fileContext`() = runTest {
        val fileContextProvider = FileContextProvider.getInstance(projectRule.project)
        val fileContextProviderSpy = spy(fileContextProvider)
        projectRule.project.replaceService(FileContextProvider::class.java, fileContextProviderSpy, disposableRule.disposable)

        // codewhispererService uses CALLS_REAL_METHODS, so getRequestContext already calls real method

        val requestContext = codewhispererService.getRequestContext(
            TriggerTypeInfo(CodewhispererTriggerType.AutoTrigger, CodeWhispererAutomatedTriggerType.Enter()),
            editor = projectRule.fixture.editor,
            project = projectRule.project,
            file,
            LatencyContext()
        )

        assertThat(requestContext.fileContextInfo).isEqualTo(
            FileContextInfo(
                CaretContext(leftFileContext = "", rightFileContext = "public class Main {}", leftContextOnCurrentLine = ""),
                "main.java",
                CodeWhispererJava.INSTANCE,
                "main.java",
                file.virtualFile.url
            )
        )
    }

    @Test
    fun `getRequestContext should have customizationArn if it's present`() = runTest {
        whenever(customizationConfig.activeCustomization(projectRule.project)).thenReturn(
            CodeWhispererCustomization(
                "fake-arn",
                "fake-name",
                ""
            )
        )

        val mockFileContextProvider = mock<FileContextProvider> {
            on { this.extractFileContext(any(), any()) } doReturn aFileContextInfo()
        }

        projectRule.project.replaceService(FileContextProvider::class.java, mockFileContextProvider, disposableRule.disposable)
        // codewhispererService uses CALLS_REAL_METHODS, so getRequestContext already calls real method

        val actual = codewhispererService.getRequestContext(
            TriggerTypeInfo(CodewhispererTriggerType.OnDemand, CodeWhispererAutomatedTriggerType.Unknown()),
            projectRule.fixture.editor,
            projectRule.project,
            file,
            LatencyContext()
        )

        assertThat(actual.customizationArn).isEqualTo("fake-arn")
    }

    @Test
    fun `test handleInlineCompletion creates correct params and sends to server`() = runTest {
        val mockEditor = projectRule.fixture.editor

        val capturedParams = codewhispererService.createInlineCompletionParams(
            mockEditor,
            TriggerTypeInfo(CodewhispererTriggerType.OnDemand, CodeWhispererAutomatedTriggerType.Unknown()),
            null
        )

        runReadAction {
            assertThat(capturedParams.textDocument.uri).isEqualTo(toUriString(file.virtualFile))
            assertThat(capturedParams.position.line).isEqualTo(mockEditor.caretModel.primaryCaret.visualPosition.line)
            assertThat(capturedParams.position.character).isEqualTo(mockEditor.caretModel.primaryCaret.offset)
            assertThat(capturedParams.context.triggerKind).isEqualTo(InlineCompletionTriggerKind.Invoke)
        }
    }
}
