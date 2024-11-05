// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry.otel

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit
import software.aws.toolkits.jetbrains.services.telemetry.MetricEventMetadata
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import java.time.Instant
import kotlin.time.Duration.Companion.nanoseconds

class ToolkitTelemetryOTelSpanProcessor : SpanProcessor {
    override fun isStartRequired() = false
    override fun isEndRequired() = true

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}

    override fun onEnd(span: ReadableSpan) {
        val data = span.toSpanData()

        TelemetryService.getInstance().record(MetricEventMetadata(
            awsProduct = AWSProduct.fromValue(data.attributes.get(PLUGIN_NAME_ATTRIBUTE_KEY)) ?: AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS,
            awsVersion = "unknown"
        )) {
            createTime(Instant.ofEpochSecond(0L, data.startEpochNanos))

            datum(data.name) {
                val attributes = data.attributes.asMap().entries.associate { it.key.key to it.value }.toMutableMap()
                attributes.remove(PLUGIN_NAME_ATTRIBUTE_KEY.key)

                passive(attributes.remove("passive") as Boolean)
                unit(MetricUnit.fromValue(attributes.remove("unit") as String))
                value(attributes.remove("value") as Double)

                attributes.forEach { t, u ->
                    metadata(t, u.toString())
                }

                // auto-duration
                if (attributes["duration"] == null && data.endEpochNanos != 0L) {
                    metadata("duration", (data.endEpochNanos - data.startEpochNanos).nanoseconds.inWholeMilliseconds.toString())
                }
            }
        }
    }
}
