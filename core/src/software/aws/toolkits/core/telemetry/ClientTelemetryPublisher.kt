// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.telemetry

import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum
import software.amazon.awssdk.services.toolkittelemetry.model.Unit
import software.aws.toolkits.core.utils.getLogger
import kotlin.streams.toList

class ClientTelemetryPublisher(
    private val productName: String,
    private val productVersion: String,
    private val clientId: String,
    private val client: ToolkitTelemetryClient
) : MetricsPublisher {

    override fun publishMetrics(metrics: Collection<Metric>): Boolean = try {
        client.postMetrics {
            it.awsProduct(productName)
            it.awsProductVersion(productVersion)
            it.clientID(clientId)
            it.metricData(metrics.toMetricData())
        }
        true
    } catch (e: Exception) {
        LOG.warn("Failed to publish metrics", e)
        false
    }

    override fun shutdown() { }

    private fun Collection<Metric>.toMetricData(): Collection<MetricDatum> = this.stream()
        .flatMap { metric ->
            metric.entries.entries.stream().map { entry -> MetricDatum.builder()
                    .epochTimestamp(metric.createTime.toEpochMilli())
                    .metricName("${metric.metricNamespace}.${entry.key}")
                    .unit(entry.value.unit.toSdkUnit())
                    .value(entry.value.value)
                    .build()
            }
        }
        .toList()

    private fun MetricUnit.toSdkUnit(): Unit = when (this) {
        MetricUnit.BYTES -> Unit.BYTES
        MetricUnit.COUNT -> Unit.COUNT
        MetricUnit.MILLISECONDS -> Unit.MILLISECONDS
        MetricUnit.PERCENT -> Unit.PERCENT
    }

    private companion object {
        private val LOG = getLogger<ClientTelemetryPublisher>()
    }
}
