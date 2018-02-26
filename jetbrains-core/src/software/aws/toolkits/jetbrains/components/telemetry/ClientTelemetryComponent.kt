// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.components.telemetry

import com.intellij.openapi.components.ApplicationComponent
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.aws.toolkits.core.telemetry.BatchingMetricsPublisher
import software.aws.toolkits.core.telemetry.ClientTelemetryPublisher
import software.aws.toolkits.core.telemetry.MetricUnit
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.net.URI
import java.time.Duration
import java.time.ZonedDateTime

interface ClientTelemetryComponent : ApplicationComponent

class DefaultClientTelemetryComponent : ClientTelemetryComponent {
    private lateinit var publisher: BatchingMetricsPublisher
    private lateinit var startupTime: ZonedDateTime

    override fun initComponent() {
        super.initComponent()

        publisher = BatchingMetricsPublisher(ClientTelemetryPublisher(
            "AWS Toolkit For JetBrains",
            AwsToolkit.PLUGIN_VERSION,
            AwsSettings.getInstance().clientId.toString(),
            ToolkitTelemetryClient
                    .builder()
                    // TODO: This is the beta endpoint. Replace with the production endpoint before release.
                    .endpointOverride(URI.create("https://7zftft3lj2.execute-api.us-east-1.amazonaws.com/Beta"))
                    // TODO: Determine why this client is not picked up by default.
                    .httpClient(ApacheHttpClient.builder().build())
                    .build()
        ))

        publisher.newMetric("ToolkitStart").use {
            startupTime = it.createTime
            // TODO: This is a workaround due the the backend dropping events with no datums.
            //       Remove once the backend is fixed.
            it.addMetricEntry("placeholder", 0.0, MetricUnit.COUNT)
            publisher.publishMetric(it)
        }
    }

    override fun disposeComponent() {
        try {
            publisher.newMetric("ToolkitEnd").use {
                val duration = Duration.between(startupTime, it.createTime)
                it.addMetricEntry("duration", duration.toMillis().toDouble(), MetricUnit.MILLISECONDS)
                publisher.publishMetric(it)
            }
        } finally {
            publisher.shutdown()
            super.disposeComponent()
        }
    }

    override fun getComponentName(): String = javaClass.simpleName
}
