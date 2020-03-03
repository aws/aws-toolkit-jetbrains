// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.telemetry

import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment

interface TelemetryPublisher {
    fun publish(metricEvents: Collection<MetricEvent>): Boolean
    fun sendFeedback(sentiment: Sentiment, comment: String)
}
