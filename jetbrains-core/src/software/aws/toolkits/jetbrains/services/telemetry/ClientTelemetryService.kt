// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.Disposable
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.aws.toolkits.core.telemetry.MetricUnit
import software.aws.toolkits.core.telemetry.MetricsPublisher
import software.aws.toolkits.core.telemetry.NoOpMetricsPublisher
import software.aws.toolkits.core.telemetry.BatchingMetricsPublisher
import software.aws.toolkits.core.telemetry.ClientTelemetryPublisher
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.net.URI
import java.time.Duration
import java.time.Instant

interface ClientTelemetryService : Disposable

class DefaultClientTelemetryService(sdkClient: AwsSdkClient) : ClientTelemetryService {
    private lateinit var startupTime: Instant
    private val publisher: MetricsPublisher

    init {
        Disposer.register(ApplicationManager.getApplication(), sdkClient)

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
                            .httpClient(sdkClient.sdkHttpClient)
                            .build()
            ))
        } else {
            NoOpMetricsPublisher()
        }

        publisher.newMetric("ToolkitStart").use {
            startupTime = it.createTime
            publisher.publishMetric(it)
        }
    }

    override fun dispose() {
        try {
            publisher.newMetric("ToolkitEnd").use {
                val duration = Duration.between(startupTime, it.createTime)
                it.addMetricEntry("duration", duration.toMillis().toDouble(), MetricUnit.MILLISECONDS)
                publisher.publishMetric(it)
            }
        } finally {
            publisher.shutdown()
        }
    }
}
