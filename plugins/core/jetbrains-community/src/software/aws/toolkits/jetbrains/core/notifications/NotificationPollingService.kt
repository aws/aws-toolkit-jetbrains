// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import com.intellij.util.io.HttpRequests
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val NOTIFICATION_ENDPOINT = "https://idetoolkits-hostedfiles.amazonaws.com/Notifications/JetBrains/1.json" // TODO: Replace with actual endpoint
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 1000L

@Service
class NotificationPollingService : Disposable {
    /**
     * Data class representing the structure of notifications from the endpoint
     */
    data class NotificationFile(
        val notifications: List<Notification>,
        val version: String,
    )

    data class Notification(
        val id: String,
        val message: String,
        val criteria: NotificationCriteria,
    )

    data class NotificationCriteria(
        val minVersion: String?,
        val maxVersion: String?,
        val regions: List<String>?,
        val ideType: String?,
        val pluginVersion: String?,
        val os: String?,
        val authType: String?,
        val authRegion: String?,
        val authState: String?,
        val authScopes: List<String>?,
        val installedPlugins: List<String>?,
        val computeEnvironment: String?,
        val messageType: String?,
    )
    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val LOG = getLogger<NotificationPollingService>()
    private var currentETag: String? = null
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)

    init {
        startPolling()
    }

    override fun dispose() {
        alarm.dispose()
    }

    private fun startPolling() {
        pollForNotifications()

        alarm.addRequest(
            { startPolling() },
            Duration.ofMinutes(10).toMillis()
        )
    }

    /**
     * Main polling function that checks for updates and downloads if necessary
     * Returns the parsed notifications if successful, null otherwise
     */
    private fun pollForNotifications(): NotificationFile? {
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < MAX_RETRIES) {
            try {
                // Check if there are updates available
                val newETag = getNotificationETag()

                if (newETag == currentETag) {
                    LOG.debug { "No updates available for notifications" }
                    return loadLocalNotifications()
                }

                // Download and process new notifications
                val notifications = downloadAndProcessNotifications(newETag)
                currentETag = newETag
                return notifications
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

        // After all retries failed, emit metric and return null
        emitFailureMetric(lastException)
        return loadLocalNotifications()
    }

    private fun getNotificationETag(): String =
        HttpRequests.request(NOTIFICATION_ENDPOINT)
            .userAgent("AWS Toolkit for JetBrains")
            .connect { request ->
                request.connection.headerFields["ETag"]?.firstOrNull() ?: ""
            }

    private fun downloadAndProcessNotifications(newETag: String): NotificationFile {
        val content = HttpRequests.request(NOTIFICATION_ENDPOINT)
            .userAgent("AWS Toolkit for JetBrains")
            .readString()

        // Save to local file for backup
        saveLocalNotifications(content)

        return deserializeNotifications(content)
    }

    /**
     * Deserializes the notification content with error handling
     */
    private fun deserializeNotifications(content: String): NotificationFile {
        try {
            return mapper.readValue(content)
        } catch (e: Exception) {
            LOG.error(e) { "Failed to deserialize notifications" }
            throw e
        }
    }

    /**
     * Loads notifications, preferring the latest downloaded version if available,
     * falling back to bundled resource if no downloaded version exists
     */
    private fun loadLocalNotifications(): NotificationFile? {
        // First try to load from system directory (latest downloaded version)
        getLocalNotificationsPath().let { path ->
            if (path.exists()) {
                try {
                    val content = path.readText()
                    return deserializeNotifications(content)
                } catch (e: Exception) {
                    LOG.error(e) { "Failed to load downloaded notifications, falling back to bundled resource" }
                }
            }
        }

        // Fall back to bundled resource if no downloaded version exists
        return try {
            val resourceStream = javaClass.getResourceAsStream(NOTIFICATIONS_RESOURCE_PATH)
                ?: return null

            val content = resourceStream.use { stream ->
                stream.bufferedReader().readText()
            }

            deserializeNotifications(content)
        } catch (e: Exception) {
            LOG.error(e) { "Failed to load notifications from bundled resources" }
            null
        }
    }

    /**
     * Saves downloaded notifications to system directory
     */
    private fun saveLocalNotifications(content: String) {
        try {
            val path = getLocalNotificationsPath()
            path.parent.createDirectories()
            path.writeText(content)
        } catch (e: IOException) {
            LOG.error(e) { "Failed to save notifications to local storage" }
        }
    }

    /**
     * Gets the path for downloaded notifications in IntelliJ's system directory
     */
    private fun getLocalNotificationsPath(): Path {
        return Path.of(PathManager.getSystemPath())
            .resolve("aws-toolkit")
            .resolve("notifications")
            .resolve("notifications.json")
    }

    /**
     * Emits telemetry metric for polling failures
     */
    private fun emitFailureMetric(exception: Exception?) {
        // todo: add metric
        // toolkit
    }

    companion object {
        private const val NOTIFICATIONS_RESOURCE_PATH = "/software/aws/toolkits/resources/notifications.json"
    }
}
