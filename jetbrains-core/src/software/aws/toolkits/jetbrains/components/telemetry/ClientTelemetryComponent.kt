// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.components.telemetry

import com.intellij.openapi.components.ApplicationComponent
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.aws.toolkits.core.telemetry.*
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.net.URI
import java.time.Duration
import java.time.Instant

interface ClientTelemetryComponent : ApplicationComponent

class DefaultClientTelemetryComponent : ClientTelemetryComponent {
    private lateinit var startupTime: Instant
    private lateinit var publisher: MetricsPublisher

    override fun initComponent() {
        super.initComponent()

            val settings = AwsSettings.getInstance()
            publisher = if (settings.isTelemetryEnabled) {
                BatchingMetricsPublisher(ClientTelemetryPublisher(
                        AwsToolkit.PLUGIN_NAME,
                        AwsToolkit.PLUGIN_VERSION,
                        settings.clientId.toString(),
                        ToolkitTelemetryClient
                                .builder()
                                // TODO: This is the beta endpoint. Replace with the production endpoint before release.
                                .endpointOverride(URI.create("https://7zftft3lj2.execute-api.us-east-1.amazonaws.com/Beta"))
                                // TODO: Determine why this client is not picked up by default.
                                .httpClient(AwsSdkClient.getInstance().sdkHttpClient)
                                .build()
                ))
            } else {
                NoOpMetricsPublisher()
            }

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
