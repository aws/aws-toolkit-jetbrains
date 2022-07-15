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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonTestLeftContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager.Companion.CODEWHISPERER_USER_ACTION_PERFORMED
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererCodeCoverageTracker
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererCodeCoverageTracker.Companion.FIVE_MINS_IN_SECS
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.telemetry.NoOpPublisher
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import kotlin.math.roundToInt

class CodeWhispererCodeCoverageTrackerTest {
    private class TestCodePercentageTracker(
        project: Project,
        timeWindowInSec: Long,
        acceptedTokensBuffer: StringBuilder = StringBuilder(),
        totalTokensBuffer: StringBuilder = StringBuilder()
    ) : CodeWhispererCodeCoverageTracker(project, timeWindowInSec, acceptedTokensBuffer, totalTokensBuffer)
    private class TestTelemetryService(
        publisher: TelemetryPublisher = NoOpPublisher(),
        batcher: TelemetryBatcher
    ) : TelemetryService(publisher, batcher)

    @Rule
    @JvmField
    var projectRule = PythonCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val mockClientManagerRule = MockClientManagerRule()

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private lateinit var project: Project
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var telemetryServiceSpy: TelemetryService
    private lateinit var batcher: TelemetryBatcher
    private lateinit var trackerSpy: CodeWhispererCodeCoverageTracker

    private lateinit var invocationContext: InvocationContext
    private lateinit var sessionContext: SessionContext

    @Before
    fun setup() {
        project = projectRule.project
        fixture = projectRule.fixture
        fixture.configureByText(CodeWhispererTestUtil.pythonFileName, pythonTestLeftContext)
        runInEdtAndWait {
            projectRule.fixture.editor.caretModel.primaryCaret.moveToOffset(projectRule.fixture.editor.document.textLength)
        }

        batcher = mock()
        telemetryServiceSpy = spy(TestTelemetryService(batcher = batcher))
        trackerSpy = spy(TestCodePercentageTracker(project, FIVE_MINS_IN_SECS, StringBuilder(), StringBuilder()))

        ApplicationManager.getApplication().replaceService(TelemetryService::class.java, telemetryServiceSpy, disposableRule.disposable)
        project.replaceService(CodeWhispererCodeCoverageTracker::class.java, trackerSpy, disposableRule.disposable)
        project.replaceService(CodeWhispererCodeReferenceManager::class.java, mock(), disposableRule.disposable)

        val requestContext = RequestContext(project, fixture.editor, mock(), mock(), mock(), mock())
        val responseContext = ResponseContext("sessionId", CodewhispererCompletionType.Block)
        val recommendationContext = RecommendationContext(
            listOf(DetailContext("requestId", pythonResponse.recommendations()[0], pythonResponse.recommendations()[0], false)),
            "x, y",
            "x, y",
            mock()
        )
        invocationContext = InvocationContext(requestContext, responseContext, recommendationContext, mock())
        sessionContext = SessionContext()
    }

    @Test
    fun `test document changes will be appended to totalTokens`() {
        val testFile = fixture.addFileToProject("/test.py", "")
        val string = """
            def add(x, y):
                return x + y                                    
        """.trimIndent()
        runInEdtAndWait {
            fixture.openFileInEditor(testFile.virtualFile)
            WriteCommandAction.runWriteCommandAction(project) {
                fixture.editor.appendString(string)
            }
        }
        assertThat(trackerSpy.totalTokens.length).isEqualTo(string.length)
    }

    @Test
    fun `test CODEWHISPERER_USER_ACTION_PERFORMED will append accepted tokens`() {
        val remainingRecomm = "):\n\treturn x + y"
        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_USER_ACTION_PERFORMED).afterAccept(
            invocationContext,
            sessionContext,
            remainingRecomm
        )
        assertThat(trackerSpy.acceptedTokens.length).isEqualTo(remainingRecomm.length)
    }

    @Test
    fun `test edge case 0 token will return 0 %`() {
        AwsSettings.getInstance().isTelemetryEnabled = true
        val emptyFile = fixture.addFileToProject("/emptyFile.py", "")
        runInEdtAndWait {
            fixture.openFileInEditor(emptyFile.virtualFile)
            trackerSpy.flush()
        }

        val metricCaptor = argumentCaptor<MetricEvent>()
        verify(batcher, Times(2)).enqueue(metricCaptor.capture())
        CodeWhispererTelemetryTest.assertEventsContainsFieldsAndCount(
            metricCaptor.allValues,
            CODE_PERCENTAGE,
            1,
            "codewhispererPercentage" to "0"
        )
    }

    @Test
    fun `test flush() will emit telemetry and reset tokens`() {
        AwsSettings.getInstance().isTelemetryEnabled = true
        trackerSpy = spy(TestCodePercentageTracker(project, 1L, StringBuilder("bar"), StringBuilder("foo bar")))
        project.replaceService(CodeWhispererCodeCoverageTracker::class.java, trackerSpy, disposableRule.disposable)

        assertThat(trackerSpy.acceptedTokens).isEqualTo("bar")
        assertThat(trackerSpy.totalTokens).isEqualTo("foo bar")
        trackerSpy.flush()

        val percentage = ("bar".length.toDouble() / "foo bar".length * 100).roundToInt()
        val metricCaptor = argumentCaptor<MetricEvent>()
        verify(batcher, atLeastOnce()).enqueue(metricCaptor.capture())
        CodeWhispererTelemetryTest.assertEventsContainsFieldsAndCount(
            metricCaptor.allValues,
            CODE_PERCENTAGE,
            1,
            "codewhispererPercentage" to percentage.toString()
        )
        assertThat(trackerSpy.acceptedTokens).isEqualTo("")
        assertThat(trackerSpy.totalTokens).isEqualTo("")
    }

    @Test
    fun `test flush() will automatically reschedule`() {
        AwsSettings.getInstance().isTelemetryEnabled = true
        doNothing().`when`(trackerSpy).scheduleCodeWhispererTracker()
        trackerSpy.flush()
        verify(trackerSpy, Times(1)).scheduleCodeWhispererTracker()
    }

    @Test
    fun `test flush() won't emit telemetry when users not enabling telemetry service`() {
        AwsSettings.getInstance().isTelemetryEnabled = false
        trackerSpy.flush()
        verify(batcher, Times(0)).enqueue(any())
    }

    private fun Editor.appendString(string: String) {
        val currentOffset = caretModel.primaryCaret.offset
        document.insertString(currentOffset, string)
        caretModel.moveToOffset(currentOffset + string.length)
    }

    companion object {
        const val CODE_PERCENTAGE = "codewhisperer_codePercentage"
    }
}
