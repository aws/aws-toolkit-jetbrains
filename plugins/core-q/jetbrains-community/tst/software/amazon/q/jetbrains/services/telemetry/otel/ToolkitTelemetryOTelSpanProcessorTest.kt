// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanId
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit
import software.amazon.q.jetbrains.services.telemetry.TelemetryService
import software.amazon.q.core.telemetry.DefaultMetricEvent
import software.amazon.q.core.telemetry.DefaultMetricEvent.DefaultDatum
import software.amazon.q.core.telemetry.TelemetryBatcher
import software.amazon.q.core.telemetry.TelemetryPublisher
import software.amazon.q.jetbrains.services.telemetry.NoOpPublisher
import software.amazon.q.jetbrains.utils.satisfiesKt
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
        val otelOnlyFields = setOf("traceId", "metricId", "parentId")

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

        Telemetry.codewhisperer.serviceInvocation.setStartTimestamp(createTime).use {
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

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(2)).enqueue(capture())

            val filteredOtelEvent = secondValue.copy(
                data = secondValue.data.map { data ->
                    (data as DefaultDatum).copy(
                        metadata = data.metadata.filter { it.key !in otelOnlyFields }
                    )
                }
            )
            assertThat(filteredOtelEvent).isEqualTo(firstValue)
        }
    }

    @Test
    fun `OTel emits trace ids`() {
        val span = Telemetry.toolkit.init.startSpan()
        span.end()

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(1)).enqueue(capture())

            val traceId = span.spanContext.traceId
            val metricId = span.spanContext.spanId
            assertThat(firstValue.data).singleElement().satisfiesKt { input ->
                assertThat(input.metadata).containsEntry("traceId", traceId)
                assertThat(input.metadata).containsEntry("metricId", metricId)
            }
        }
    }

    @Test
    fun `OTel emits trace ids from parent`() {
        lateinit var childSpan: Span
        val parentSpan = Telemetry.toolkit.init.use { parent ->
            childSpan = Telemetry.toolkit.init.use { child ->
                child
            }
            parent
        }

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(2)).enqueue(capture())

            assertThat(firstValue.data).singleElement().satisfiesKt { input ->
                assertThat(input.metadata).containsEntry("traceId", childSpan.spanContext.traceId)
                assertThat(input.metadata).containsEntry("metricId", childSpan.spanContext.spanId)
                assertThat(input.metadata).containsEntry("parentId", parentSpan.spanContext.spanId)
            }

            assertThat(secondValue.data).singleElement().satisfiesKt { input ->
                assertThat(input.metadata).containsEntry("traceId", parentSpan.spanContext.traceId)
                assertThat(input.metadata).containsEntry("metricId", parentSpan.spanContext.spanId)
                assertThat(input.metadata).containsEntry("parentId", SpanId.getInvalid())
            }
        }
    }

    @Test
    fun `automatically applies duration on metric without duration`() = runTest {
        Telemetry.toolkit.init.useWithScope {
            delay(10_000)
        }

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(1)).enqueue(capture())

            assertThat(firstValue.data).singleElement().satisfiesKt { input ->
                assertThat(input.metadata).containsKey("duration")
            }
        }
    }

    @Test
    fun `respects duration on metric if defined`() {
        Telemetry.toolkit.init.use {
            it.duration(12_345.6)
        }

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(1)).enqueue(capture())

            assertThat(firstValue.data).singleElement().satisfiesKt { input ->
                assertThat(input.metadata).containsEntry("duration", "12345.6")
            }
        }
    }

    @Test
    fun `strips metadata fields that need special handling`() {
        Telemetry.toolkit.init.use {
            it.passive(true)
            it.unit(MetricUnit.COUNT)
            it.value(99999)
        }

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(1)).enqueue(capture())

            assertThat(firstValue.data).singleElement().satisfiesKt { input ->
                assertThat(input.metadata).containsOnlyKeys("duration", "metricId", "parentId", "traceId")
            }
        }
    }

    @Test
    fun `attaches fields as metadata`() {
        Telemetry.codewhisperer.serviceInvocation.use {
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

        argumentCaptor<DefaultMetricEvent> {
            verify(batcher, times(1)).enqueue(capture())

            assertThat(firstValue.data).singleElement().satisfiesKt { input ->
                // tracing ids tested in different case
                assertThat(input.metadata).containsAllEntriesOf(
                    mapOf(
                        "codewhispererAutomatedTriggerType" to "Enter",
                        "codewhispererCompletionType" to "Line",
                        "codewhispererCursorOffset" to "123",
                        "codewhispererCustomizationArn" to "codewhispererCustomizationArn",
                        "codewhispererLanguage" to "python",
                        "codewhispererLineNumber" to "0",
                        "codewhispererTriggerType" to "AutoTrigger",
                        "duration" to "0.0",
                        "result" to "Succeeded",
                    )
                )
            }
        }
    }
}
