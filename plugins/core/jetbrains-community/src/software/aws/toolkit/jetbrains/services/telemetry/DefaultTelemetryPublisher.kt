// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.services.telemetry

import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.amazon.awssdk.services.toolkittelemetry.model.MetadataEntry
import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkit.core.clients.nullDefaultProfileFile
import software.aws.toolkit.core.telemetry.MetricEvent
import software.aws.toolkit.core.telemetry.TelemetryPublisher
import software.aws.toolkit.jetbrains.core.AwsClientManager
import software.aws.toolkit.jetbrains.core.AwsSdkClient
import software.aws.toolkit.jetbrains.core.coroutines.getCoroutineBgContext

class DefaultTelemetryPublisher(
    private val clientProvider: () -> ToolkitTelemetryClient,
    private val clientMetadata: ClientMetadata,
) : TelemetryPublisher {
    constructor() : this(
        clientProvider = { createDefaultTelemetryClient() },
        clientMetadata = ClientMetadata.DEFAULT_METADATA
    )

    private val lazyClient = lazy { clientProvider() }
    private val client by lazyClient

    override suspend fun publish(metricEvents: Collection<MetricEvent>) {
        withContext(getCoroutineBgContext()) {
            metricEvents.groupBy { Pair(it.awsProduct, it.awsVersion) }
                .forEach { (productName, productVersion), events ->
                    client.postMetrics {
                        it.awsProduct(clientMetadata.productName)
                        it.awsProductVersion(clientMetadata.productVersion)
                        it.clientID(clientMetadata.clientId)
                        it.os(clientMetadata.os)
                        it.osVersion(clientMetadata.osVersion)
                        it.parentProduct(clientMetadata.parentProduct)
                        it.parentProductVersion(clientMetadata.parentProductVersion)
                        it.metricData(events.toMetricData())
                    }
                }
        }
    }

    override suspend fun sendFeedback(sentiment: Sentiment, comment: String, metadata: Map<String, String>) {
        withContext(getCoroutineBgContext()) {
            client.postFeedback {
                it.awsProduct(clientMetadata.productName)
                it.awsProductVersion(clientMetadata.productVersion)
                it.os(clientMetadata.os)
                it.osVersion(clientMetadata.osVersion)
                it.parentProduct(clientMetadata.parentProduct)
                it.parentProductVersion(clientMetadata.parentProductVersion)
                it.sentiment(sentiment)
                it.comment(comment)
                if (metadata.isNotEmpty()) {
                    it.metadata(metadata.map { (k, v) -> MetadataEntry.builder().key(k).value(v).build() })
                }
            }
        }
    }

    private fun Collection<MetricEvent>.toMetricData(): Collection<MetricDatum> = this
        .flatMap { metricEvent ->
            metricEvent.data.map { datum ->
                val metricName = datum.name
                MetricDatum.builder()
                    .epochTimestamp(metricEvent.createTime.toEpochMilli())
                    .metricName(metricName)
                    .unit(datum.unit)
                    .value(datum.value)
                    .passive(datum.passive)
                    .metadata(
                        datum.metadata.entries.stream().map {
                            MetadataEntry.builder()
                                .key(it.key)
                                .value(it.value)
                                .build()
                        }.toList() + listOf(
                            MetadataEntry.builder()
                                .key(METADATA_AWS_ACCOUNT)
                                .value(metricEvent.awsAccount)
                                .build(),
                            MetadataEntry.builder()
                                .key(METADATA_AWS_REGION)
                                .value(metricEvent.awsRegion)
                                .build()
                        )
                    )
                    .build()
            }
        }

    override fun close() {
        if (lazyClient.isInitialized()) {
            client.close()
        }
    }

    private companion object {
        private const val METADATA_AWS_ACCOUNT = "awsAccount"
        private const val METADATA_AWS_REGION = "awsRegion"

        private fun createDefaultTelemetryClient(): ToolkitTelemetryClient {
            val region = Region.of(Registry.get("aws.telemetry.region").asString())
            val sdkClient = AwsSdkClient.Companion.getInstance()

            return AwsClientManager.Companion.getInstance().createUnmanagedClient(
                credProvider = AwsCognitoCredentialsProvider(
                    Registry.get("aws.telemetry.identityPool").asString(),
                    CognitoIdentityClient.builder()
                        .credentialsProvider(AnonymousCredentialsProvider.create())
                        .region(region)
                        .httpClient(sdkClient.sharedSdkClient())
                        .nullDefaultProfileFile()
                        .build()
                ),
                region = region,
                endpointOverride = Registry.get("aws.telemetry.endpoint").asString()
            ) { _, _, _, _, clientOverrideConfiguration -> clientOverrideConfiguration.nullDefaultProfileFile() }
        }
    }
}
