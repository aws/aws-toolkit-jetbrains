// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.DefaultProjectFactory
import software.amazon.awssdk.services.toolkittelemetry.ToolkitTelemetryClient
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct
import software.amazon.awssdk.services.toolkittelemetry.model.Unit
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.region.ToolkitRegionProvider
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.AWSCognitoCredentialsProvider
import software.aws.toolkits.core.telemetry.DefaultTelemetryBatcher
import software.aws.toolkits.core.telemetry.DefaultTelemetryPublisher
import software.aws.toolkits.core.telemetry.DefaultMetricEvent
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.TelemetryEnabledChangedNotifier
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface TelemetryService : Disposable {
    fun record(buildEvent: MetricEvent.Builder.() -> kotlin.Unit)
}

class DefaultTelemetryService(
    sdkClient: AwsSdkClient,
    regionProvider: ToolkitRegionProvider,
    publishInterval: Long = DEFAULT_PUBLISH_INTERVAL,
    publishIntervalUnit: TimeUnit = DEFAULT_PUBLISH_INTERVAL_UNIT,
    private val executor: ScheduledExecutorService = createDefaultExecutor()
) : TelemetryService {
    private val isDisposing: AtomicBoolean = AtomicBoolean(false)
    private val batcher: TelemetryBatcher
    private val startTime: Instant

    init {
        val settings = AwsSettings.getInstance()
        val clientManager = ServiceManager.getService(
                DefaultProjectFactory.getInstance().defaultProject,
                ToolkitClientManager::class.java
        )
        val client: ToolkitTelemetryClient = ToolkitTelemetryClient
                .builder()
                .endpointOverride(URI.create("https://client-telemetry.us-east-1.amazonaws.com"))
                // TODO: Determine why this client is not picked up by default.
                .httpClient(sdkClient.sdkHttpClient)
                .credentialsProvider(AWSCognitoCredentialsProvider(
                        "us-east-1:820fd6d1-95c0-4ca4-bffb-3f01d32da842",
                        clientManager,
                        regionProvider
                ))
                .build()
        val publisher = DefaultTelemetryPublisher(
                AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS,
                AwsToolkit.PLUGIN_VERSION,
                settings.clientId.toString(),
                ApplicationNamesInfo.getInstance().fullProductNameWithEdition,
                ApplicationInfo.getInstance().fullVersion,
                client
        )
        batcher = DefaultTelemetryBatcher(publisher)

        ApplicationManager.getApplication().messageBus.connect().subscribe(
                AwsSettings.TELEMETRY_ENABLED_CHANGED,
                object : TelemetryEnabledChangedNotifier {
                    override fun notify(isTelemetryEnabled: Boolean) {
                        batcher.onTelemetryEnabledChanged(isTelemetryEnabled)
                    }
                }
        )

        executor.scheduleWithFixedDelay(
                PublishActivity(),
                publishInterval,
                publishInterval,
                publishIntervalUnit
        )

        startTime = Instant.now()
        record {
            namespace("ToolkitStart")
            createTime(startTime)
            datum {
                name("metadata")
                value(0.0)
                unit(Unit.COUNT)
            }
        }
    }

    override fun dispose() {
        if (!isDisposing.compareAndSet(false, true)) {
            return
        }

        executor.shutdown()

        val endTime = Instant.now()
        record {
            namespace("ToolkitEnd")
            createTime(endTime)
            datum {
                name("duration")
                value(Duration.between(startTime, endTime).toMillis().toDouble())
                unit(Unit.MILLISECONDS)
            }
        }

        batcher.shutdown()
    }

    override fun record(buildEvent: MetricEvent.Builder.() -> kotlin.Unit) {
        val builder = DefaultMetricEvent.builder()
        buildEvent(builder)
        batcher.enqueue(builder.build())
    }

    private inner class PublishActivity : Runnable {
        override fun run() {
            if (isDisposing.get()) {
                return
            }
            try {
                batcher.flush(true)
            } catch (e: Exception) {
                LOG.warn("Unexpected exception while publishing telemetry", e)
            }
        }
    }

    companion object {
        private val LOG = getLogger<TelemetryService>()
        private const val DEFAULT_PUBLISH_INTERVAL = 5L
        private val DEFAULT_PUBLISH_INTERVAL_UNIT = TimeUnit.MINUTES

        private fun createDefaultExecutor() = Executors.newSingleThreadScheduledExecutor {
            val daemonThread = Thread(it)
            daemonThread.isDaemon = true
            daemonThread.name = "AWS-Toolkit-Metrics-Publisher"
            daemonThread
        }
    }
}
