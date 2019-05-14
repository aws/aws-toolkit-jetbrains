// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.SystemInfo
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.amazon.awssdk.services.toolkittelemetry.model.MetadataEntry
import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import software.aws.toolkits.jetbrains.settings.AwsSettings
import kotlin.streams.toList
import software.amazon.awssdk.services.toolkittelemetry.model.Unit as MetricUnit

class DefaultTelemetryPublisher(
    private val productName: AWSProduct = AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS,
    private val productVersion: String = AwsToolkit.PLUGIN_VERSION,
    private val clientId: String = AwsSettings.getInstance().clientId.toString(),
    private val parentProduct: String = ApplicationNamesInfo.getInstance().fullProductNameWithEdition,
    private val parentProductVersion: String = ApplicationInfo.getInstance().fullVersion,
    private val client: ToolkitTelemetryClient = createDefaultTelemetryClient(),
    private val os: String = SystemInfo.OS_NAME,
    private val osVersion: String = SystemInfo.OS_VERSION
) : TelemetryPublisher {
    override fun publish(metricEvents: Collection<MetricEvent>): Boolean = ApplicationManager.getApplication().executeOnPooledThread<Boolean> {
        try {
            client.postMetrics {
                it.awsProduct(productName)
                it.awsProductVersion(productVersion)
                it.clientID(clientId)
                it.os(os)
                it.osVersion(osVersion)
                it.parentProduct(parentProduct)
                it.parentProductVersion(parentProductVersion)
                it.metricData(metricEvents.toMetricData())
            }
            true
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to publish metrics" }
            false
        }
    }.get()

    private fun Collection<MetricEvent>.toMetricData(): Collection<MetricDatum> = this
        .flatMap { metricEvent ->
            metricEvent.data.map { datum ->
                MetricDatum.builder()
                    .epochTimestamp(metricEvent.createTime.toEpochMilli())
                    .metricName("${metricEvent.namespace}.${datum.name}")
                    .unit(datum.unit)
                    .value(datum.value)
                    .metadata(datum.metadata.entries.stream().map {
                        MetadataEntry.builder()
                            .key(it.key)
                            .value(it.value)
                            .build()
                    }.toList())
                    .build()
            }.ifEmpty {
                listOf(
                    MetricDatum.builder()
                        .epochTimestamp(metricEvent.createTime.toEpochMilli())
                        .metricName(metricEvent.namespace)
                        .unit(MetricUnit.NONE)
                        .value(0.0)
                        .build()
                )
            }
        }

    private companion object {
        private val LOG = getLogger<DefaultTelemetryPublisher>()

        private fun createDefaultTelemetryClient(): ToolkitTelemetryClient {
            val sdkClient = AwsSdkClient.getInstance()
            return ToolkitClientManager.createNewClient(
                ToolkitTelemetryClient::class,
                sdkClient.sdkHttpClient,
                Region.US_EAST_1,
                AWSCognitoCredentialsProvider(
                    "us-east-1:820fd6d1-95c0-4ca4-bffb-3f01d32da842",
                    CognitoIdentityClient.builder()
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(Region.US_EAST_1)
                        .httpClient(sdkClient.sdkHttpClient)
                        .build()),
                AwsClientManager.userAgent,
                "https://client-telemetry.us-east-1.amazonaws.com"
            )
        }
    }
}
