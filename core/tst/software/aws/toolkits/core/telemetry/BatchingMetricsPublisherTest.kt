package software.aws.toolkits.core.telemetry

import assertk.assert
import assertk.assertions.hasSize
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.stubbing.Answer
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.mockito.Mockito.`when` as whenever

class BatchingMetricsPublisherTest {
    @Rule
    @JvmField
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var downstreamPublisher: MetricsPublisher

    @Captor
    private lateinit var recordEventsCaptor: ArgumentCaptor<Collection<Metric>>

    private lateinit var batchingMetricsPublisher: BatchingMetricsPublisher

    @Before
    fun setUp() {
        batchingMetricsPublisher = BatchingMetricsPublisher(
            downstreamPublisher,
            10,
            TimeUnit.MILLISECONDS,
            MAX_BATCH_SIZE
        )
    }

    @Test fun testSingleBatch() {
        val publishCount = CountDownLatch(1)

        whenever(downstreamPublisher.publishMetrics(capturePublishMetrics()))
            .then(createPublishAnswer(publishCount, true))

        batchingMetricsPublisher.newMetric(EVENT_NAME).close()

        waitForPublish(publishCount)

        verify(downstreamPublisher).publishMetrics(anyCollection<Metric>())

        assert(recordEventsCaptor.value).hasSize(1)
    }

    @Test fun testSplitBatch() {
        // Will publish in 2 batches
        val publishCount = CountDownLatch(2)

        whenever(downstreamPublisher.publishMetrics(capturePublishMetrics()))
            .then(createPublishAnswer(publishCount, true))

        val totalEvents = MAX_BATCH_SIZE + 1
        val events = ArrayList<Metric>(totalEvents)
        for (i in 0 until totalEvents) {
            events.add(batchingMetricsPublisher.newMetric(EVENT_NAME))
        }
        batchingMetricsPublisher.publishMetrics(events)

        waitForPublish(publishCount)

        verify(downstreamPublisher, times(2)).publishMetrics(anyCollection<Metric>())

        assert(recordEventsCaptor.allValues).hasSize(2)
        assert(recordEventsCaptor.allValues[0]).hasSize(MAX_BATCH_SIZE)
        assert(recordEventsCaptor.allValues[1]).hasSize(totalEvents - MAX_BATCH_SIZE)
    }

    @Test fun testRetry() {
        val publishCount = CountDownLatch(2)

        whenever(downstreamPublisher.publishMetrics(capturePublishMetrics()))
            .then(createPublishAnswer(publishCount, false))
            .then(createPublishAnswer(publishCount, true))

        batchingMetricsPublisher.newMetric(EVENT_NAME).close()

        waitForPublish(publishCount)

        verify(downstreamPublisher, times(2)).publishMetrics(anyCollection<Metric>())

        assert(recordEventsCaptor.allValues).hasSize(2)
        assert(recordEventsCaptor.allValues[0]).hasSize(1)
        assert(recordEventsCaptor.allValues[1]).hasSize(1)
    }

    @Test fun testRetryException() {
        val publishCount = CountDownLatch(1)

        whenever(downstreamPublisher.publishMetrics(capturePublishMetrics()))
            .thenThrow(RuntimeException("Mock exception"))
            .then(createPublishAnswer(publishCount, true))

        batchingMetricsPublisher.newMetric(EVENT_NAME).close()

        waitForPublish(publishCount)

        verify(downstreamPublisher, times(2)).publishMetrics(anyCollection<Metric>())

        assert(recordEventsCaptor.allValues).hasSize(2)
        assert(recordEventsCaptor.allValues[0]).hasSize(1)
        assert(recordEventsCaptor.allValues[1]).hasSize(1)
    }

    @Test fun testShutdown() {
        whenever(downstreamPublisher.publishMetrics(capturePublishMetrics()))
            .thenReturn(true)

        batchingMetricsPublisher.newMetric(EVENT_NAME).close()
        batchingMetricsPublisher.shutdown()
        // This will get ignored since we marked shutdown to begin
        batchingMetricsPublisher.newMetric(EVENT_NAME).close()
        batchingMetricsPublisher.shutdown()

        verify(downstreamPublisher).publishMetrics(anyCollection<Metric>())

        assert(recordEventsCaptor.allValues).hasSize(1)
        assert(recordEventsCaptor.value).hasSize(1)
    }

    private fun waitForPublish(publishCount: CountDownLatch) {
        // Wait for maximum of 5 secs before thread continues, may not reach final count though
        publishCount.await(5, TimeUnit.SECONDS)
    }

    // Used to work around the null checks caused by Kotlin and ArgumentCaptor.capture()
    private fun capturePublishMetrics(): Collection<Metric> = recordEventsCaptor.capture() ?: emptyList<Metric>()

    private fun createPublishAnswer(publishCount: CountDownLatch, value: Boolean): Answer<Boolean> {
        return Answer {
            publishCount.countDown();
            value;
        }
    }

    companion object {
        private const val EVENT_NAME = "Event"
        private const val MAX_BATCH_SIZE = 5
    }
}