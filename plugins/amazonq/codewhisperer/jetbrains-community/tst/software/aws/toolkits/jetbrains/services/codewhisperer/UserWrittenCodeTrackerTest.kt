// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonTestLeftContext
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.QFeatureEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.UserWrittenCodeTracker
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.UserWrittenCodeTracker.Companion.Q_FEATURE_TOPIC
import software.aws.toolkits.jetbrains.services.telemetry.NoOpPublisher
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule

internal class UserWrittenCodeTrackerTest {

    internal class TestTelemetryService(
        publisher: TelemetryPublisher = NoOpPublisher(),
        batcher: TelemetryBatcher,
    ) : TelemetryService(publisher, batcher)

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    @Rule
    @JvmField
    val mockClientManagerRule = MockClientManagerRule()

    @Rule
    @JvmField
    var projectRule = PythonCodeInsightTestFixtureRule()

    lateinit var project: Project
    lateinit var fixture: CodeInsightTestFixture
    lateinit var telemetryServiceSpy: TelemetryService
    lateinit var batcher: TelemetryBatcher
    lateinit var exploreActionManagerMock: CodeWhispererExplorerActionManager
    lateinit var sut: UserWrittenCodeTracker

    @Before
    open fun setup() {
        this.project = projectRule.project
        this.fixture = projectRule.fixture
        fixture.configureByText(pythonFileName, pythonTestLeftContext)
        AwsSettings.getInstance().isTelemetryEnabled = true
        batcher = mock()

        exploreActionManagerMock = mock {
            on { checkActiveCodeWhispererConnectionType(any()) } doReturn CodeWhispererLoginType.Sono
        }

        ApplicationManager.getApplication().replaceService(CodeWhispererExplorerActionManager::class.java, exploreActionManagerMock, disposableRule.disposable)

        fixture.configureByText(pythonFileName, pythonTestLeftContext)
        runInEdtAndWait {
            projectRule.fixture.editor.caretModel.primaryCaret.moveToOffset(projectRule.fixture.editor.document.textLength)
        }
    }

    @After
    fun tearDown() {
        if (::sut.isInitialized) {
            sut.forceTrackerFlush()
            sut.reset()
        }
    }

    @Test
    fun `test tracker is listening to q  service invocation`() {
        sut = UserWrittenCodeTracker.getInstance(project)
        sut.activateTrackerIfNotActive()
        assertThat(sut.qInvocationCount.get()).isEqualTo(0)
        ApplicationManager.getApplication().messageBus.syncPublisher(Q_FEATURE_TOPIC).onEvent(QFeatureEvent.INVOCATION)
        assertThat(sut.qInvocationCount.get()).isEqualTo(1)
        ApplicationManager.getApplication().messageBus.syncPublisher(Q_FEATURE_TOPIC).onEvent(QFeatureEvent.INVOCATION)
        assertThat(sut.qInvocationCount.get()).isEqualTo(2)
    }

    @Test
    fun `test tracker is not listening to multi char input more than 50, but works for less than 50, and will not increment totalTokens - add new code`() {
        sut = UserWrittenCodeTracker.getInstance(project)
        sut.activateTrackerIfNotActive()
        fixture.configureByText(pythonFileName, "")
        val newCode = "def addTwoNumbers\n   return"
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.appendString(newCode)
            }
        }
        val language: CodeWhispererProgrammingLanguage = CodeWhispererPython.INSTANCE
        assertThat(sut.userWrittenCodeCharacterCount[language]).isEqualTo(newCode.length.toLong())
        assertThat(sut.userWrittenCodeLineCount[language]).isEqualTo(1)

        val anotherCode = "(x, y):\n".repeat(8)
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.appendString(anotherCode)
            }
        }
        assertThat(sut.userWrittenCodeCharacterCount[language]).isEqualTo(newCode.length.toLong())
        assertThat(sut.userWrittenCodeLineCount[language]).isEqualTo(1)
    }

    @Test
    fun `test tracker is listening to document changes and increment totalTokens - delete code should not affect`() {
        sut = UserWrittenCodeTracker.getInstance(project)
        sut.activateTrackerIfNotActive()
        assertThat(sut.userWrittenCodeCharacterCount.getOrDefault(CodeWhispererPython.INSTANCE, 0)).isEqualTo(0)
        runInEdtAndWait {
            fixture.editor.caretModel.primaryCaret.moveToOffset(fixture.editor.document.textLength)
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.document.deleteString(fixture.editor.caretModel.offset - 3, fixture.editor.caretModel.offset)
            }
        }
        assertThat(sut.userWrittenCodeCharacterCount.getOrDefault(CodeWhispererPython.INSTANCE, 0)).isEqualTo(0)
    }

    @Test
    fun `test tracker is listening to document changes only when Q is not editing`() {
        sut = UserWrittenCodeTracker.getInstance(project)
        sut.activateTrackerIfNotActive()
        fixture.configureByText(pythonFileName, "")
        val newCode = "def addTwoNumbers\n   return"

        ApplicationManager.getApplication().messageBus.syncPublisher(Q_FEATURE_TOPIC).onEvent(QFeatureEvent.STARTS_EDITING)
        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.appendString(newCode)
            }
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(Q_FEATURE_TOPIC).onEvent(QFeatureEvent.FINISHES_EDITING)
        val language: CodeWhispererProgrammingLanguage = CodeWhispererPython.INSTANCE
        assertThat(sut.userWrittenCodeCharacterCount.getOrDefault(language, 0)).isEqualTo(0)
        assertThat(sut.userWrittenCodeLineCount.getOrDefault(language, 0)).isEqualTo(0)

        runInEdtAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.appendString(newCode)
            }
        }
        assertThat(sut.userWrittenCodeCharacterCount[CodeWhispererPython.INSTANCE]).isEqualTo(newCode.length.toLong())
        assertThat(sut.userWrittenCodeLineCount[CodeWhispererPython.INSTANCE]).isEqualTo(1)
    }

    private fun Editor.appendString(string: String) {
        val currentOffset = caretModel.primaryCaret.offset
        document.insertString(currentOffset, string)
        caretModel.moveToOffset(currentOffset + string.length)
    }
}
