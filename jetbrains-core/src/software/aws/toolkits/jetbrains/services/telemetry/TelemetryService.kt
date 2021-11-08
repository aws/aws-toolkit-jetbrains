// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.telemetry.DefaultMetricEvent
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_INVALID
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_NA
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_NOT_SET
import software.aws.toolkits.core.telemetry.DefaultTelemetryBatcher
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettings
import software.aws.toolkits.jetbrains.core.getResourceIfPresent
import software.aws.toolkits.jetbrains.services.sts.StsResources
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.util.concurrent.atomic.AtomicBoolean

data class MetricEventMetadata(
    val awsAccount: String = METADATA_NA,
    val awsRegion: String = METADATA_NA
)

interface TelemetryListener {
    fun onTelemetryEvent(event: MetricEvent)
}

abstract class TelemetryService(private val publisher: TelemetryPublisher, private val batcher: TelemetryBatcher) : Disposable {
    private val isDisposing = AtomicBoolean(false)
    private val listeners = mutableSetOf<TelemetryListener>()

    init {
        setTelemetryEnabled(AwsSettings.getInstance().isTelemetryEnabled)
    }

    fun record(connectionSettings: ConnectionSettings?, buildEvent: MetricEvent.Builder.() -> Unit) {
        val metricEventMetadata = when (connectionSettings) {
            is ConnectionSettings -> MetricEventMetadata(
                awsAccount = connectionSettings.activeAwsAccountIfKnown() ?: METADATA_NOT_SET,
                awsRegion = connectionSettings.region.id
            )
            else -> MetricEventMetadata()
        }
        record(metricEventMetadata, buildEvent)
    }

    fun record(project: Project?, buildEvent: MetricEvent.Builder.() -> Unit) {
        // It is possible that a race can happen if we record telemetry but project has been closed, i.e. async actions
        val metricEventMetadata = if (project != null) {
            if (project.isDisposed) {
                MetricEventMetadata(
                    awsAccount = METADATA_INVALID,
                    awsRegion = METADATA_INVALID
                )
            } else {
                MetricEventMetadata(
                    awsAccount = project.getConnectionSettings()?.activeAwsAccountIfKnown() ?: METADATA_NOT_SET,
                    awsRegion = project.activeRegion().id
                )
            }
        } else {
            MetricEventMetadata()
        }
        record(metricEventMetadata, buildEvent)
    }

    private fun ConnectionSettings.activeAwsAccountIfKnown(): String? = tryOrNull { this.getResourceIfPresent(StsResources.ACCOUNT) }

    @Synchronized
    fun setTelemetryEnabled(isEnabled: Boolean) {
        batcher.onTelemetryEnabledChanged(isEnabled and TELEMETRY_ENABLED)
    }

    fun addListener(listener: TelemetryListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: TelemetryListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        if (!isDisposing.compareAndSet(false, true)) {
            return
        }

        listeners.clear()

        batcher.shutdown()
    }

    fun record(metricEventMetadata: MetricEventMetadata, buildEvent: MetricEvent.Builder.() -> Unit) {
        val builder = DefaultMetricEvent.builder()
        builder.awsAccount(metricEventMetadata.awsAccount)
        builder.awsRegion(metricEventMetadata.awsRegion)

        buildEvent(builder)

        val event = builder.build()

        runCatching {
            listeners.forEach { it.onTelemetryEvent(event) }
        }

        batcher.enqueue(event)
    }

    suspend fun sendFeedback(sentiment: Sentiment, comment: String) {
        publisher.sendFeedback(sentiment, comment)
    }

    companion object {
        private const val TELEMETRY_KEY = "aws.toolkits.enableTelemetry"
        private val TELEMETRY_ENABLED = System.getProperty(TELEMETRY_KEY)?.toBoolean() ?: true

        fun getInstance(): TelemetryService = service()
    }
}

class DefaultTelemetryService : TelemetryService(PUBLISHER, DefaultTelemetryBatcher(PUBLISHER)) {
    private companion object {
        val PUBLISHER = DefaultTelemetryPublisher()
    }
}
