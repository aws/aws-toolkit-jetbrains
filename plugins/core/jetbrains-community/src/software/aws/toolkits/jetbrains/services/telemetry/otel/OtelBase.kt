// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry.otel

import com.intellij.openapi.application.ApplicationManager
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope
import io.opentelemetry.sdk.trace.ReadWriteSpan
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata
import software.aws.toolkits.telemetry.impl.BaseSpan
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import com.intellij.platform.diagnostic.telemetry.helpers.use as ijUse
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope as ijUseWithScope

val AWS_PRODUCT_CONTEXT_KEY = ContextKey.named<AWSProduct>("pluginDescriptor")
val PLUGIN_NAME_ATTRIBUTE_KEY = AttributeKey.stringKey("pluginName")

class DefaultSpan(context: Context?, delegate: Span) : BaseSpan<DefaultSpan>(context, delegate)

class DefaultSpanBuilder(delegate: SpanBuilder) : AbstractSpanBuilder<DefaultSpanBuilder, DefaultSpan>(delegate) {
    override fun doStartSpan() = DefaultSpan(parent, delegate.startSpan())
}

abstract class AbstractSpanBuilder<
    BuilderType : AbstractSpanBuilder<BuilderType, SpanType>,
    SpanType : AbstractBaseSpan<SpanType>,
    >(
    protected val delegate: SpanBuilder,
) : SpanBuilder {
    /**
     * Same as [com.intellij.platform.diagnostic.telemetry.helpers.use] except downcasts to specific subclass of [BaseSpan]
     *
     * @inheritdoc
     */
    inline fun<T> use(operation: (SpanType) -> T): T =
        startSpan().ijUse { span ->
            operation(span as SpanType)
        }

    /**
     * Same as [com.intellij.platform.diagnostic.telemetry.helpers.useWithScope] except downcasts to specific subclass of [BaseSpan]
     *
     * @inheritdoc
     */
    suspend inline fun<T> useWithScope(
        context: CoroutineContext = EmptyCoroutineContext,
        crossinline operation: suspend CoroutineScope.(SpanType) -> T,
    ): T =
        ijUseWithScope(context) { span ->
            operation(span as SpanType)
        }

    protected var parent: Context? = null
    override fun setParent(context: Context): BuilderType {
        parent = context
        delegate.setParent(context)
        return this as BuilderType
    }

    override fun setNoParent(): BuilderType {
        parent = null
        delegate.setNoParent()
        return this as BuilderType
    }

    override fun addLink(spanContext: SpanContext): BuilderType {
        delegate.addLink(spanContext)
        return this as BuilderType
    }

    override fun addLink(
        spanContext: SpanContext,
        attributes: Attributes,
    ): BuilderType {
        delegate.addLink(spanContext, attributes)
        return this as BuilderType
    }

    override fun setAttribute(key: String, value: String): BuilderType {
        delegate.setAttribute(key, value)
        return this as BuilderType
    }

    override fun setAttribute(key: String, value: Long): BuilderType {
        delegate.setAttribute(key, value)
        return this as BuilderType
    }

    override fun setAttribute(key: String, value: Double): BuilderType {
        delegate.setAttribute(key, value)
        return this as BuilderType
    }

    override fun setAttribute(key: String, value: Boolean): BuilderType {
        delegate.setAttribute(key, value)
        return this as BuilderType
    }

    override fun <V : Any?> setAttribute(
        key: AttributeKey<V?>,
        value: V & Any,
    ): BuilderType {
        delegate.setAttribute(key, value)
        return this as BuilderType
    }

    override fun setAllAttributes(attributes: Attributes): BuilderType {
        delegate.setAllAttributes(attributes)
        return this as BuilderType
    }

    override fun setSpanKind(spanKind: SpanKind): BuilderType {
        delegate.setSpanKind(spanKind)
        return this as BuilderType
    }

    override fun setStartTimestamp(startTimestamp: Long, unit: TimeUnit): BuilderType {
        delegate.setStartTimestamp(startTimestamp, unit)
        return this as BuilderType
    }

    override fun setStartTimestamp(startTimestamp: Instant): BuilderType {
        delegate.setStartTimestamp(startTimestamp)
        return this as BuilderType
    }

    protected abstract fun doStartSpan(): SpanType

    override fun startSpan(): SpanType {
        var parent = parent
        if (parent == null) {
            parent = Context.current()
        }
        requireNotNull(parent)

        val contextValue = parent.get(AWS_PRODUCT_CONTEXT_KEY)
        if (contextValue == null) {
            val s = Span.fromContextOrNull(parent)
            parent = if (s is AbstractBaseSpan<*> && s.context != null) {
                s.context.with(Span.fromContext(parent))
            } else {
                parent.with(AWS_PRODUCT_CONTEXT_KEY, resolvePluginName())
            }
            setParent(parent)
        }
        requireNotNull(parent)

        parent.get(AWS_PRODUCT_CONTEXT_KEY)?.toString()?.let {
            setAttribute(PLUGIN_NAME_ATTRIBUTE_KEY, it)
        } ?: run {
            LOG.warn { "Reached setAttribute with null AWS_PRODUCT_CONTEXT_KEY, but should not be possible" }
        }

        return doStartSpan()
    }

    private companion object {
        val LOG = getLogger<AbstractSpanBuilder<*, *>>()
        fun resolvePluginName() = ClientMetadata.DEFAULT_METADATA.productName
    }
}

abstract class AbstractBaseSpan<SpanType : AbstractBaseSpan<SpanType>>(internal val context: Context?, private val delegate: ReadWriteSpan) : Span by delegate {
    protected open val requiredFields: Collection<String> = emptySet()
    private var passive: Boolean = false
    private var unit: MetricUnit = MetricUnit.NONE
    private var value: Double = 1.0

    /**
     * Same as [com.intellij.platform.diagnostic.telemetry.helpers.use] except downcasts to specific subclass of [BaseSpan]
     *
     * @inheritdoc
     */
    inline fun<T> use(operation: (SpanType) -> T): T =
        ijUse { span ->
            operation(span as SpanType)
        }

    fun metadata(key: String, value: String?): SpanType {
        delegate.setAttribute(key, value)
        return this as SpanType
    }

    override fun recordException(exception: Throwable): SpanType {
        delegate.recordException(exception)

        setAttribute("reason", exception::class.java.canonicalName)
        setAttribute("reasonDesc", exception.message)
        return this as SpanType
    }

    override fun end() {
        finalize()
        validateRequiredAttributes()
        delegate.end()
    }

    override fun end(timestamp: Long, unit: TimeUnit) {
        finalize()
        validateRequiredAttributes()
        delegate.end()
    }

    fun passive(passive: Boolean): SpanType {
        this.passive = passive
        return this as SpanType
    }

    fun unit(unit: MetricUnit): SpanType {
        this.unit = unit
        return this as SpanType
    }

    fun value(value: Number): SpanType {
        this.value = value.toDouble()
        return this as SpanType
    }

    private fun finalize() {
        setAttribute("passive", passive)
        setAttribute("unit", unit.toString())
        setAttribute("value", value)
    }

    private fun validateRequiredAttributes() {
        val missingFields = requiredFields.filter { delegate.getAttribute(AttributeKey.stringKey(it)) == null }
        val message = { "${delegate.name} is missing required fields: ${missingFields.joinToString(", ")}" }

        if (missingFields.isNotEmpty()) {
            when {
                ApplicationManager.getApplication().isUnitTestMode -> error(message())
                isDeveloperMode() -> LOG.error { message() }
                else -> LOG.error { message() }
            }
        }
    }

    override fun makeCurrent(): Scope =
        context?.with(this)?.makeCurrent() ?: super.makeCurrent()

    private companion object {
        val LOG = getLogger<AbstractBaseSpan<*>>()
    }
}
