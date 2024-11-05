import com.intellij.openapi.Disposable
import org.assertj.core.api.Assertions.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.jetbrains.services.telemetry.NoOpPublisher
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.telemetry.CodewhispererAutomatedTriggerType
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.time.Instant

@ExtendWith(ApplicationExtension::class)
class ToolkitTelemetryOTelSpanProcessorTest {
    private class TestTelemetryService(
        publisher: TelemetryPublisher = NoOpPublisher(),
        batcher: TelemetryBatcher,
    ) : TelemetryService(publisher, batcher)

    private lateinit var telemetryService: TelemetryService
    private lateinit var batcher: TelemetryBatcher

    @BeforeEach
    fun setUp(@TestDisposable disposable: Disposable) {
        batcher = mock()
        telemetryService = spy(TestTelemetryService(batcher = batcher))
        ApplicationManager.getApplication().replaceService(TelemetryService::class.java, telemetryService, disposable)
    }

    @Test
    fun `OTel emits same payload as old metrics`() {
        val createTime = Instant.now()
        CodewhispererTelemetry.serviceInvocation(
            project = null,
            codewhispererAutomatedTriggerType = CodewhispererAutomatedTriggerType.Enter,
            codewhispererCompletionType = CodewhispererCompletionType.Line,
            codewhispererCursorOffset = 123,
            codewhispererCustomizationArn = "codewhispererCustomizationArn",
            codewhispererLanguage = CodewhispererLanguage.Python,
            codewhispererLineNumber = 0,
            codewhispererTriggerType = CodewhispererTriggerType.AutoTrigger,
            duration = 0.0,
            result = MetricResult.Succeeded,
            createTime = createTime,
        )

        Telemetry.codewhisperer.serviceInvocation.setStartTimestamp(createTime).startSpan().use {
            it.codewhispererAutomatedTriggerType(CodewhispererAutomatedTriggerType.Enter)
                .codewhispererCompletionType(CodewhispererCompletionType.Line)
                .codewhispererCursorOffset(123)
                .codewhispererCustomizationArn("codewhispererCustomizationArn")
                .codewhispererLanguage(CodewhispererLanguage.Python)
                .codewhispererLineNumber(0)
                .codewhispererTriggerType(CodewhispererTriggerType.AutoTrigger)
                .duration(0.0)
                .result(MetricResult.Succeeded)
        }

        argumentCaptor<MetricEvent> {
            verify(batcher, times(2)).enqueue(capture())

            assertThat(firstValue).isEqualTo(secondValue)
        }
    }
}
