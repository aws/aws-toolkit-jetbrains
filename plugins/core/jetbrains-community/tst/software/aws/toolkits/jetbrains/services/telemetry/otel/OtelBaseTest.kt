// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry.otel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationExtension
import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import software.aws.toolkits.jetbrains.utils.satisfiesKt
import software.aws.toolkits.jetbrains.utils.spinUntil
import java.util.concurrent.TimeUnit

@ExtendWith(ApplicationExtension::class)
class OtelBaseTest {
    private companion object {
        @RegisterExtension
        val otelExtension = OtelExtension()
    }

    @Test
    fun `context propagates from parent to child - happy case`() {
        spanBuilder("tracer", "parentSpan").use {
            spanBuilder("anotherTracer", "childSpan").use {}
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context propagates from parent to child - happy case coroutines`() = runTest {
        spanBuilder("tracer", "parentSpan").useWithScope {
            spanBuilder("anotherTracer", "childSpan").useWithScope {}
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context propagates from parent to child - with context override`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            spanBuilder("anotherTracer", "childSpan").use {}
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context propagates from parent to child when child overrides context`() {
        spanBuilder("tracer", "parentSpan").use {
            // parent->child relationship is still maintained because Context.current() will return parent context
            spanBuilder("anotherTracer", "childSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {}
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isNotEqualTo(child.getAttribute(PLUGIN_ATTRIBUTE_KEY))
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
        }
    }

    @Test
    fun `context override does not propagate from parent to child when switching threads`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            ApplicationManager.getApplication().executeOnPooledThread {
                spanBuilder("anotherTracer", "childSpan").use {}
            }.get(10, TimeUnit.SECONDS)
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isNotEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context override propagates from parent to child when switching threads while preserving thread-local`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            pluginAwareExecuteOnPooledThread {
                spanBuilder("anotherTracer", "childSpan").use {}
            }.get(10, TimeUnit.SECONDS)
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context override propagates from parent to child when only child is coroutine`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            runTest {
                spanBuilder("anotherTracer", "childSpan").useWithScope {
                    delay(10000)
                }
            }
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context override propagates from parent to child when only parent is coroutine`() = runTest {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).useWithScope {
            spanBuilder("anotherTracer", "childSpan").use {}
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context override propagates from parent to child when both are coroutines`() = runTest {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).useWithScope {
            spanBuilder("anotherTracer", "childSpan").useWithScope {}
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context override does not propagate from parent to child coroutines if context is not preserved`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            runBlocking(getCoroutineBgContext()) {
                spanBuilder("anotherTracer", "childSpan").use {}
            }
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isNotEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context override propagates from parent to child coroutines with manual coroutine context propagation`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            runBlocking(getCoroutineBgContext() + Context.current().asContextElement()) {
                spanBuilder("anotherTracer", "childSpan").use {}
            }
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.first()
            val child = spans.last()

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    @Test
    fun `context propagates from parent to child when child#end is after parent#end`() {
        spanBuilder("tracer", "parentSpan").setParent(Context.current().with(AWS_PRODUCT_CONTEXT_KEY, AWSProduct.AMAZON_Q_FOR_VS_CODE)).use {
            pluginAwareExecuteOnPooledThread {
                spanBuilder("anotherTracer", "childSpan").use {
                    Thread.sleep(100)
                }
            }
        }
        spinUntil(java.time.Duration.ofSeconds(10)) {
            otelExtension.completedSpans.size == 2
        }

        assertThat(otelExtension.completedSpans).hasSize(2).satisfiesKt { spans ->
            val parent = spans.last()
            val child = spans.first()

            assertThat(parent.hasEnded())
            assertThat(child.hasEnded())

            // child started after parent
            assertThat(child.toSpanData().startEpochNanos).isGreaterThanOrEqualTo(parent.toSpanData().startEpochNanos)
            // and called end after parent
            assertThat(child.toSpanData().endEpochNanos).isGreaterThanOrEqualTo(parent.toSpanData().endEpochNanos)

            assertThat(parent.parentSpanContext.traceId).isEqualTo(TraceId.getInvalid())
            assertThat(child.parentSpanContext.traceId).isEqualTo(parent.spanContext.traceId)
            assertThat(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo("Amazon Q For VS Code")
            assertThat(child.getAttribute(PLUGIN_ATTRIBUTE_KEY)).isEqualTo(parent.getAttribute(PLUGIN_ATTRIBUTE_KEY))
        }
    }

    private fun spanBuilder(tracer: String, spanName: String) = DefaultSpanBuilder(otelExtension.sdk.sdk.getTracer(tracer).spanBuilder(spanName))
}

class OtelExtension : AfterEachCallback, AfterAllCallback {
    private val openSpans = mutableSetOf<ReadableSpan>()
    private val _completedSpans = mutableListOf<ReadableSpan>()
    val completedSpans
        get() = _completedSpans.reversed().toList()

    val sdk = OTelService(
        listOf(
            // should probably be a service loader
            object : SpanProcessor {
                override fun isStartRequired() = true
                override fun isEndRequired() = true

                override fun onStart(parentContext: Context, span: ReadWriteSpan) {
                    openSpans.add(span)
                }

                override fun onEnd(span: ReadableSpan) {
                    _completedSpans.add(span)

                    if (!openSpans.contains(span)) {
                        LOG.warn(RuntimeException("Span ended without corresponding start")) { span.toString() }
                    }
                    openSpans.remove(span)
                }

                override fun forceFlush(): CompletableResultCode {
                    assert(openSpans.isEmpty()) { "Not all open spans were closed: ${openSpans.joinToString(", ")}" }
                    return CompletableResultCode.ofSuccess()
                }
            }
        )
    )

    override fun afterEach(context: ExtensionContext?) {
        reset()
    }

    override fun afterAll(context: ExtensionContext?) {
        sdk.sdk.shutdown()
    }

    fun reset() {
        openSpans.clear()
        _completedSpans.clear()
    }

    companion object {
        private val LOG = getLogger<OtelExtension>()
    }
}
