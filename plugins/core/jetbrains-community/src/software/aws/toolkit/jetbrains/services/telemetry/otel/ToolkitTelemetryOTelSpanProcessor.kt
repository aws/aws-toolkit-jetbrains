// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.services.telemetry.otel

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit
import software.aws.toolkit.core.utils.tryOrNull
import software.aws.toolkit.jetbrains.AwsPlugin
import software.aws.toolkit.jetbrains.AwsToolkit
import software.aws.toolkit.jetbrains.services.telemetry.MetricEventMetadata
import software.aws.toolkit.jetbrains.services.telemetry.TelemetryService
import java.time.Instant
import kotlin.time.Duration.Companion.nanoseconds

class ToolkitTelemetryOTelSpanProcessor : SpanProcessor {
    override fun isStartRequired() = false
    override fun isEndRequired() = true

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}

    override fun onEnd(span: ReadableSpan) {
        val data = span.toSpanData()
        val product = data.attributes.get(PLUGIN_NAME_ATTRIBUTE_KEY)?.let { AWSProduct.fromValue(it) } ?: AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS
        val version = tryOrNull {
            when (product) {
                AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS -> AwsToolkit.PLUGINS_INFO[AwsPlugin.TOOLKIT]?.version
                AWSProduct.AMAZON_Q_FOR_JET_BRAINS -> AwsToolkit.PLUGINS_INFO[AwsPlugin.Q]?.version
                else -> null
            }
        } ?: "unknown"

        TelemetryService.getInstance().record(
            MetricEventMetadata(
                awsProduct = product,
                awsVersion = version,
            )
        ) {
            createTime(Instant.ofEpochSecond(0L, data.startEpochNanos))

            datum(data.name) {
                val attributes = data.attributes.asMap().entries.associate { it.key.key to it.value }.toMutableMap()
                // goes on root of payload
                attributes.remove(PLUGIN_NAME_ATTRIBUTE_KEY.key)

                // special handling attributes
                passive(attributes.remove("passive") as Boolean)
                unit(MetricUnit.fromValue(attributes.remove("unit") as String))
                value(attributes.remove("value") as Double)

                // everything else
                attributes.forEach { t, u ->
                    metadata(t, u.toString())
                }

                // auto-duration
                if (attributes["duration"] == null && data.endEpochNanos != 0L) {
                    metadata("duration", (data.endEpochNanos - data.startEpochNanos).nanoseconds.inWholeMilliseconds.toString())
                }

                // the reason why we used opentelemetry
                metadata("traceId", data.traceId)
                metadata("metricId", data.spanId)
                metadata("parentId", data.parentSpanId)
            }
        }
    }
}
