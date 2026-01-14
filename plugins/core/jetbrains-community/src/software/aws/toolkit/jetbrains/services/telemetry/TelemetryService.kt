// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.services.telemetry

import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.aws.toolkit.core.telemetry.DefaultMetricEvent.Companion.METADATA_NA
import software.aws.toolkit.core.telemetry.DefaultTelemetryBatcher
import software.aws.toolkit.core.telemetry.MetricEvent
import software.aws.toolkit.core.telemetry.TelemetryBatcher
import software.aws.toolkit.core.telemetry.TelemetryPublisher

typealias TelemetryService = migration.software.aws.toolkit.jetbrains.services.telemetry.TelemetryService

data class MetricEventMetadata(
    val awsAccount: String = METADATA_NA,
    val awsRegion: String = METADATA_NA,
    var awsProduct: AWSProduct = AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS,
    var awsVersion: String = METADATA_NA,
)

interface TelemetryListener {
    fun onTelemetryEvent(event: MetricEvent)
}

class DefaultTelemetryService : TelemetryService(publisher, batcher) {
    private companion object {
        private val publisher: TelemetryPublisher by lazy { DefaultTelemetryPublisher() }
        private val batcher: TelemetryBatcher by lazy { DefaultTelemetryBatcher(publisher) }
    }
}
