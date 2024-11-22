// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import com.intellij.util.io.HttpRequests
import software.aws.toolkits.core.utils.RemoteResolveParser
import software.aws.toolkits.core.utils.RemoteResource
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.DefaultRemoteResourceResolverProvider
import software.aws.toolkits.jetbrains.core.RemoteResourceResolverProvider
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.ToolkitTelemetry
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

private const val NOTIFICATION_ENDPOINT = "" // TODO: Replace with actual endpoint
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 1000L

object NotificationFileValidator : RemoteResolveParser {
    override fun canBeParsed(data: InputStream): Boolean {
        return try {
            NotificationMapperUtil.mapper.readValue<NotificationsList>(data)
            true
        } catch (e: Exception) {
            false
        }
    }
}

@State(name = "NotificationETagState", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
object ETagState : PersistentStateComponent<ETagState.State> {
    data class State(
        var etag: String? = null,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }
}

@Service(Service.Level.APP)
class NotificationPollingService : Disposable {
    private val observers = mutableListOf<() -> Unit>()
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pollingIntervalMs = Duration.ofMinutes(10).toMillis()
    private val resourceResolver: RemoteResourceResolverProvider = DefaultRemoteResourceResolverProvider()
    private val notificationsResource = object : RemoteResource {
        override val name: String = "notifications.json"
        override val urls: List<String> = listOf(NOTIFICATION_ENDPOINT)
        override val remoteResolveParser: RemoteResolveParser = NotificationFileValidator
    }

    fun startPolling() {
        val newNotifications = pollForNotifications()
        if (newNotifications) {
            notifyObservers()
        }
        alarm.addRequest(
            { startPolling() },
            pollingIntervalMs
        )
    }

    /**
     * Main polling function that checks for updates and downloads if necessary
     * Returns the parsed notifications if successful, null otherwise
     */
    private fun pollForNotifications(): Boolean {
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < MAX_RETRIES) {
            try {
                val newETag = getNotificationETag()
                if (newETag == ETagState.getState().etag) {
                    if (firstPoll.compareAndSet(false, true)) {
                        notifyObservers()
                    }
                    return false
                }
                val resolvedPath = resourceResolver.get()
                    .resolve(notificationsResource)
                    .toCompletableFuture()
                    .get()
                ETagState.getState().etag = newETag
                return true
            } catch (e: Exception) {
                lastException = e
                LOG.error(e) { "Failed to poll for notifications (attempt ${retryCount + 1}/$MAX_RETRIES)" }
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    val backoffDelay = RETRY_DELAY_MS * (1L shl (retryCount - 1))
                    Thread.sleep(backoffDelay)
                }
            }
        }
        emitFailureMetric(lastException)
        return false
    }

    private fun getNotificationETag(): String =
        HttpRequests.request(NOTIFICATION_ENDPOINT)
            .userAgent("AWS Toolkit for JetBrains")
            .connect { request ->
                request.connection.headerFields["ETag"]?.firstOrNull() ?: ""
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
        private val firstPoll = AtomicBoolean(false)
        private val LOG = getLogger<NotificationPollingService>()
        fun getInstance(): NotificationPollingService =
            ApplicationManager.getApplication().getService(NotificationPollingService::class.java)
    }
}
