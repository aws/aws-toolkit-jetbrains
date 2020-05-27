// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.amazon.awssdk.services.toolkittelemetry.model.Unit.MILLISECONDS
import software.aws.toolkits.core.telemetry.DefaultMetricEvent
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_NA
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_NOT_SET
import software.aws.toolkits.core.telemetry.DefaultTelemetryBatcher
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.jetbrains.core.credentials.activeAwsAccount
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

abstract class TelemetryService(private val publisher: TelemetryPublisher, private val batcher: TelemetryBatcher) : Disposable {
    data class MetricEventMetadata(
        val awsAccount: String = METADATA_NA,
        val awsRegion: String = METADATA_NA
    )

    private val isDisposing = AtomicBoolean(false)
    private val startTime: Instant

    init {
        // TODO this startup stuff should be moved to a global startup task instead of in the constructor FIX_WHEN_MIN_IS_193
        // The auto generated telemetry cannot be used here. It tries to get the instance while
        // constructing it which causes a circular dependency issue.
        record("session_start").also {
            startTime = it.createTime
        }

        setTelemetryEnabled(AwsSettings.getInstance().isTelemetryEnabled)
    }

    fun record(project: Project?, buildEvent: MetricEvent.Builder.() -> Unit = {}): CompletableFuture<MetricEvent> {
        val metricEvent = CompletableFuture<MetricEvent>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val metricEventMetadata = if (project == null) MetricEventMetadata() else MetricEventMetadata(
                    awsAccount = project.activeAwsAccount() ?: METADATA_NOT_SET,
                    awsRegion = project.activeRegion().id
                )
                metricEvent.complete(record(metricEventMetadata, buildEvent))
            } catch (e: Exception) {
                metricEvent.completeExceptionally(e)
            }
        }
        return metricEvent
    }

    @Synchronized
    fun setTelemetryEnabled(isEnabled: Boolean) {
        batcher.onTelemetryEnabledChanged(isEnabled and TELEMETRY_ENABLED)
    }

    override fun dispose() {
        if (!isDisposing.compareAndSet(false, true)) {
            return
        }

        // Here we cannot use the auto generated telemetry because we would get the while we are are disposing the instance.
        val endTime = Instant.now()
        record {
            createTime(endTime)
            datum("session_end") {
                value(Duration.between(startTime, endTime).toMillis().toDouble())
                unit(MILLISECONDS)
            }
        }

        batcher.shutdown()
    }

    fun record(metricEventMetadata: MetricEventMetadata, buildEvent: MetricEvent.Builder.() -> Unit): MetricEvent {
        val builder = DefaultMetricEvent.builder()
        builder.awsAccount(metricEventMetadata.awsAccount)
        builder.awsRegion(metricEventMetadata.awsRegion)

        buildEvent(builder)

        val event = builder.build()

        batcher.enqueue(event)

        return event
    }

    suspend fun sendFeedback(sentiment: Sentiment, comment: String) {
        publisher.sendFeedback(sentiment, comment)
    }

    private fun record(event: MetricEvent.Builder.() -> Unit): MetricEvent = record(MetricEventMetadata(), event)

    private fun record(metricName: String): MetricEvent = record(MetricEventMetadata()) {
        this.datum(metricName)
    }

    companion object {
        private const val TELEMETRY_KEY = "aws.toolkits.enableTelemetry"
        private val TELEMETRY_ENABLED = System.getProperty(TELEMETRY_KEY)?.toBoolean() ?: true

        fun getInstance(): TelemetryService = ServiceManager.getService(TelemetryService::class.java)
    }
}

class DefaultTelemetryService : TelemetryService(PUBLISHER, DefaultTelemetryBatcher(PUBLISHER)) {
    private companion object {
        val PUBLISHER = DefaultTelemetryPublisher()
    }
}
