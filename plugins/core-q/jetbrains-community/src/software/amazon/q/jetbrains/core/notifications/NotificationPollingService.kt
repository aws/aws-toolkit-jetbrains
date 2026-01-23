// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import software.amazon.q.core.utils.RemoteResolveParser
import software.amazon.q.core.utils.RemoteResource
import software.amazon.q.core.utils.UpdateCheckResult
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.DefaultRemoteResourceResolverProvider
import software.amazon.q.jetbrains.core.RemoteResourceResolverProvider
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.ToolkitTelemetry
import java.io.InputStream
import java.time.Duration

private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 1000L
internal const val FILENAME = "notifications.json"

object NotificationFileValidator : RemoteResolveParser {
    override fun canBeParsed(data: InputStream): Boolean =
        try {
            NotificationMapperUtil.mapper.readValue<NotificationsList>(data)
            true
        } catch (e: Exception) {
            false
        }
}

object NotificationEndpoint {
    fun getEndpoint(): String =
        Registry.get("aws.toolkit.notification.endpoint").asString()
}

@Service(Service.Level.APP)
final class NotificationPollingService : Disposable {
    private val observers = mutableListOf<() -> Unit>()
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pollingIntervalMs = Duration.ofMinutes(10).toMillis()
    private val resourceResolver: RemoteResourceResolverProvider = DefaultRemoteResourceResolverProvider()
    private val notificationsResource = object : RemoteResource {
        override val name: String = FILENAME
        override val urls: List<String> = listOf(NotificationEndpoint.getEndpoint())
        override val remoteResolveParser: RemoteResolveParser = NotificationFileValidator
        override val ttl: Duration = Duration.ofMillis(1)
        // ttl forces resolver to fetch from endpoint every time
    }

    fun startPolling() {
        val newNotifications = runBlocking { pollForNotifications() }
        if (newNotifications) {
            notifyObservers()
        }
        alarm.addRequest(
            { startPolling() },
            pollingIntervalMs
        )
    }

    private suspend fun pollForNotifications(): Boolean {
        var retryCount = 0
        var lastException: Exception? = null
        while (retryCount < MAX_RETRIES) {
            LOG.info { "Polling for notifications" }
            try {
                when (
                    resourceResolver.get().checkForUpdates(
                        NotificationEndpoint.getEndpoint(),
                        NotificationEtagState.getInstance()
                    )
                ) {
                    is UpdateCheckResult.HasUpdates -> {
                        resourceResolver.get()
                            .resolve(notificationsResource)
                            .toCompletableFuture()
                            .get()
                        LOG.info { "New notifications fetched" }
                        return true
                    }
                    is UpdateCheckResult.FirstPollCheck -> {
                        LOG.info { "No new notifications, checking cached notifications on first poll" }
                        return true
                    }
                    is UpdateCheckResult.NoUpdates -> {
                        LOG.info { "No new notifications to fetch" }
                        return false
                    }
                }
            } catch (e: Exception) {
                lastException = e
                LOG.warn { "Failed to poll for notifications (attempt ${retryCount + 1}/$MAX_RETRIES)" }
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    val backoffDelay = RETRY_DELAY_MS * (1L shl (retryCount - 1))
                    delay(backoffDelay)
                }
            }
        }
        emitFailureMetric(lastException)
        return false
    }

    private fun emitFailureMetric(e: Exception?) {
        ToolkitTelemetry.showNotification(
            project = null,
            component = Component.Filesystem,
            id = "",
            reason = "Failed to poll for notifications",
            success = false,
            reasonDesc = "${e?.javaClass?.simpleName ?: "Unknown"}: ${e?.message ?: "No message"}",
        )
    }

    fun addObserver(observer: () -> Unit) = observers.add(observer)

    private fun notifyObservers() {
        observers.forEach { observer ->
            observer()
        }
    }

    override fun dispose() {
        alarm.dispose()
    }

    companion object {
        private val LOG = getLogger<NotificationPollingService>()
        fun getInstance(): NotificationPollingService =
            ApplicationManager.getApplication().getService(NotificationPollingService::class.java)
    }
}
