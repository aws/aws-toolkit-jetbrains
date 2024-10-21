// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry.otel

import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.aws.toolkits.jetbrains.services.telemetry.PluginResolver
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.use

val AWS_PRODUCT_CONTEXT_KEY = ContextKey.named<AWSProduct>("pluginDescriptor")
private val PLUGIN_ATTRIBUTE_KEY = AttributeKey.stringKey("plugin")

class DefaultSpanBuilder(delegate: SpanBuilder) : AbstractSpanBuilder<DefaultSpanBuilder, AbstractBaseSpan>(delegate) {
    override fun doStartSpan() = BaseSpan(parent!!, delegate.startSpan())
}

abstract class AbstractSpanBuilder<Builder : AbstractSpanBuilder<Builder, Span>, Span : AbstractBaseSpan>(protected val delegate: SpanBuilder) : SpanBuilder {
    /**
     * Same as [com.intellij.platform.diagnostic.telemetry.helpers.use] except downcasts to specific subclass of [BaseSpan]
     *
     * @inheritdoc
     */
    inline fun<T> use(operation: (Span) -> T): T =
        startSpan().useWithoutActiveScope { span ->
            (span as Span).makeCurrent().use {
                operation(span)
            }
        }

    protected var parent: Context? = null
    override fun setParent(context: Context): Builder {
        parent = context
        delegate.setParent(context)
        return this as Builder
    }

    override fun setNoParent(): Builder {
        parent = null
        delegate.setNoParent()
        return this as Builder
    }

    override fun addLink(spanContext: SpanContext): Builder {
        delegate.addLink(spanContext)
        return this as Builder
    }

    override fun addLink(
        spanContext: SpanContext,
        attributes: Attributes,
    ): Builder {
        delegate.addLink(spanContext, attributes)
        return this as Builder
    }

    override fun setAttribute(key: String, value: String): Builder {
        delegate.setAttribute(key, value)
        return this as Builder
    }

    override fun setAttribute(key: String, value: Long): Builder {
        delegate.setAttribute(key, value)
        return this as Builder
    }

    override fun setAttribute(key: String, value: Double): Builder {
        delegate.setAttribute(key, value)
        return this as Builder
    }

    override fun setAttribute(key: String, value: Boolean): Builder {
        delegate.setAttribute(key, value)
        return this as Builder
    }

    override fun <V : Any?> setAttribute(
        key: AttributeKey<V?>,
        value: V & Any,
    ): Builder {
        delegate.setAttribute(key, value)
        return this as Builder
    }

    override fun setAllAttributes(attributes: Attributes): Builder {
        delegate.setAllAttributes(attributes)
        return this as Builder
    }

    override fun setSpanKind(spanKind: SpanKind): Builder {
        delegate.setSpanKind(spanKind)
        return this as Builder
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): Builder {
        delegate.setStartTimestamp(startTimestamp, unit)
        return this as Builder
    }

    override fun setStartTimestamp(startTimestamp: Instant): Builder {
        delegate.setStartTimestamp(startTimestamp)
        return this as Builder
    }

    protected abstract fun doStartSpan(): Span

    override fun startSpan(): Span {
        var parent = parent
        if (parent == null) {
            parent = Context.current()
        }
        requireNotNull(parent)

        val contextValue = parent.get(AWS_PRODUCT_CONTEXT_KEY)
        if (contextValue == null) {
            val s = Span.fromContextOrNull(parent)
            if (s is AbstractBaseSpan) {
                setParent(s.context.with(Span.fromContext(parent)))
            } else {
                setParent(parent.with(AWS_PRODUCT_CONTEXT_KEY, resolvePluginName()))
            }
        }

        setAttribute(
            PLUGIN_ATTRIBUTE_KEY,
            (parent.get(AWS_PRODUCT_CONTEXT_KEY) ?: resolvePluginName()).name
        )

        return doStartSpan()
    }

    private fun resolvePluginName() = PluginResolver.Companion.fromStackTrace(Thread.currentThread().stackTrace).product
}

abstract class AbstractBaseSpan(internal val context: Context, private val delegate: Span) : Span by delegate {
    fun metadata(key: String, value: String) = setAttribute(key, value)

    override fun makeCurrent(): Scope =
        context.with(this).makeCurrent()
}

/**
 * Placeholder; will be generated
 */
class BaseSpan(context: Context, delegate: Span) : AbstractBaseSpan(context, delegate) {
    fun reason(reason: String) = metadata("reason", reason)
}
