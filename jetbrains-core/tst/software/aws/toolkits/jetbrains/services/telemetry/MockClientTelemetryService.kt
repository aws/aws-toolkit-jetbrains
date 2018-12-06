// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import software.aws.toolkits.core.telemetry.Metric
import software.aws.toolkits.core.telemetry.NoOpMetricsPublisher

class MockClientTelemetryService : ClientTelemetryService {
    override fun recordMetric(metricNamespace: String): Metric = NoOpMetricsPublisher().newMetric(metricNamespace)

    override fun dispose() {
    }
}
