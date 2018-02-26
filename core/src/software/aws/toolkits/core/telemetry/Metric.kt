package software.aws.toolkits.core.telemetry

import org.slf4j.LoggerFactory
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
    private val _datums: MutableMap<String, MetricEntry> = ConcurrentHashMap()

    val createTime = ZonedDateTime.now(ZoneOffset.UTC)
    val datums: Map<String, MetricEntry> = Collections.unmodifiableMap(_datums)

    /**
     * Finalizes the event and sends it to the [MetricsPublisher] for recording if valid
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

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
    fun addMetricDatum(metricName: String, value: Double, unit: MetricUnit): Metric {
        if (closed.get()) {
            LOG.warn("Attempted to add a metric to a closed metric", Throwable())
            return this
        }

        _datums[metricName] = MetricEntry(value, unit)
        return this
    }

    /**
     * Auto-add a sub-metric with the time to execute the code block
     *
     * @param metricName Name of the metric
     * @param action Code block to time
     * @return The new metric
     */
    fun <T> addMetricDatum(metricName: String, action: () -> T): T {
        val start = Instant.now()
        try {
            return action()
        } finally {
            val duration = Duration.between(start, Instant.now()).toMillis().toDouble()
            addMetricDatum(metricName, duration, MetricUnit.MILLISECONDS)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Metric::class.java)
    }
}

/**
 * Unit of measure for the metric
 */
enum class MetricUnit {
    BYTES, COUNT, MILLISECONDS, PERCENT
}

data class MetricEntry(val value: Double, val unit: MetricUnit)