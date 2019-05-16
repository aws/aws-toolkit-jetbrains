// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import software.aws.toolkits.core.telemetry.DefaultMetricEvent
import software.aws.toolkits.core.telemetry.MetricEvent

class MockTelemetryService : TelemetryService {
    override fun record(namespace: String, metricEventMetadata: TelemetryService.MetricEventMetadata, buildEvent: MetricEvent.Builder.() -> kotlin.Unit): MetricEvent {
        val builder = DefaultMetricEvent.builder(namespace)

        builder.datum("Metadata") {
            metricEventMetadata.awsAccount?.let { this.metadata("awsAccount", it) }
            metricEventMetadata.awsRegion?.let { this.metadata("activeAwsRegion", it) }
        }

        buildEvent(builder)
        return builder.build()
    }

    override fun dispose() {
    }
}
