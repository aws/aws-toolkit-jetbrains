// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.telemetry

import software.aws.toolkits.core.utils.getLogger
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Event that occurred in the Toolkit.
 */
open class Metric internal constructor(internal val metricNamespace: String, private val publisher: MetricsPublisher) :
    AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val _entries: MutableMap<String, MetricEntry> = ConcurrentHashMap()

    val createTime: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
    val entries: Map<String, MetricEntry> = Collections.unmodifiableMap(_entries)

    /**
     * Finalizes the event and sends it to the [MetricsPublisher] for recording if valid
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        ZonedDateTime.now(ZoneOffset.UTC)
        publisher.publishMetric(this)
    }

    /**
     * Adds a metric to the event
     *
     * @param metricName Name of the metric
     * @param value Value of the metric
     * @param unit Unit for the metric
     * @return this
     */
    fun addMetricEntry(metricName: String, value: Double, unit: MetricUnit): Metric {
        if (closed.get()) {
            LOG.warn("Attempted to add a metric to a closed metric", Throwable())
            return this
        }

        _entries[metricName] = MetricEntry(value, unit)
        return this
    }

    /**
     * Auto-add a sub-metric with the time to execute the code block
     *
     * @param metricName Name of the metric
     * @param action Code block to time
     * @return The new metric
     */
    fun <T> addMetricEntry(metricName: String, action: () -> T): T {
        val start = Instant.now()
        try {
            return action()
        } finally {
            val duration = Duration.between(start, Instant.now()).toMillis().toDouble()
            addMetricEntry(metricName, duration, MetricUnit.MILLISECONDS)
        }
    }

    companion object {
        private val LOG = getLogger<Metric>()
    }
}

/**
 * Unit of measure for the metric
 */
enum class MetricUnit {
    BYTES, COUNT, MILLISECONDS, PERCENT
}

data class MetricEntry(val value: Double, val unit: MetricUnit)