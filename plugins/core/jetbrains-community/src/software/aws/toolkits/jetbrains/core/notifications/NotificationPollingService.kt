// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import com.intellij.util.io.HttpRequests
import software.aws.toolkits.core.utils.RemoteResource
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.DefaultRemoteResourceResolverProvider
import software.aws.toolkits.jetbrains.core.RemoteResourceResolverProvider
import java.time.Duration

private const val NOTIFICATION_ENDPOINT = "https://idetoolkits-hostedfiles.amazonaws.com/Notifications/JetBrains/1.json" // TODO: Replace with actual endpoint
private const val NOTIFICATIONS_RESOURCE_PATH = "/software/aws/toolkits/resources/notifications.json"
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 1000L

interface NotificationPollingService {
    fun startPolling()
    fun dispose()
}

@Service(Service.Level.APP)
class NotificationPollingServiceImpl :
    NotificationPollingService,
    PersistentStateComponent<NotificationPollingServiceImpl.State>,
    Disposable {

    private var state = State()
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private var currentETag: String? = null // todo Persistant state? add an init possibly
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pollingIntervalMs = Duration.ofMinutes(10).toMillis()
    private val resourceResolver: RemoteResourceResolverProvider = DefaultRemoteResourceResolverProvider()
    private val notificationsResource = object : RemoteResource {
        override val name: String = NOTIFICATIONS_RESOURCE_PATH
        override val urls: List<String> = listOf(NOTIFICATION_ENDPOINT)
    }

    // PersistentStateComponent implementation
    data class State(
        var currentETag: String? = null,
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    override fun dispose() {
        alarm.dispose()
    }

    override fun startPolling() {
        pollForNotifications()

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
                if (newETag == currentETag) {
                    LOG.debug { "No updates available for notifications" }
                    return false
                }
                resourceResolver.get()
                    .resolve(notificationsResource)
                    .toCompletableFuture()
                    .get()
                currentETag = newETag
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

    /**
     * Emits telemetry metric for polling failures
     */
    private fun emitFailureMetric(exception: Exception?) {
        // todo: add metric
    }

    companion object {
        private val LOG = getLogger<NotificationPollingServiceImpl>()
        fun getInstance(): NotificationPollingService =
            ApplicationManager.getApplication().getService(NotificationPollingService::class.java)
    }
}
