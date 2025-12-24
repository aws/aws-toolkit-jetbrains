// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.amazon.q.jetbrains.core.RemoteResourceResolverProvider
import software.amazon.q.jetbrains.utils.notifyStickyWithData
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.inputStream
import software.amazon.q.core.utils.warn
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.ToolkitTelemetry
import java.util.concurrent.atomic.AtomicBoolean

object NotificationMapperUtil {
    val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
private var isStartup: AtomicBoolean = AtomicBoolean(true)

@Service(Service.Level.PROJECT)
class ProcessNotificationsBase(
    private val project: Project,
) {
    private val notifListener = mutableListOf<NotifListener>()
    init {
        LOG.info { "Initializing ProcessNotificationsBase" }
        NotificationPollingService.getInstance().addObserver {
            retrieveStartupAndEmergencyNotifications()
        }
    }

    private fun getNotificationsFromFile(): NotificationsList? {
        try {
            val path = RemoteResourceResolverProvider
                .getInstance()
                .get()
                .getLocalResourcePath(FILENAME)
            if (path == null) {
                LOG.warn { "Notifications file not found" }
                return null
            }
            val content = path.inputStream().bufferedReader().use { it.readText() }
            if (content.isEmpty()) {
                return null
            }
            return NotificationMapperUtil.mapper.readValue(content)
        } catch (e: Exception) {
            LOG.warn { "Error reading notifications file: $e" }
            return null
        }
    }

    fun retrieveStartupAndEmergencyNotifications() {
        val isStartupPoll = isStartup.compareAndSet(true, false)
        LOG.info { "Retrieving notifications for processing. StartUp notifications included: $isStartupPoll" }
        val notifications = getNotificationsFromFile()
        notifications?.let { notificationsList ->
            val activeNotifications = notificationsList.notifications
                ?.filter { notification ->
                    // Keep notification if:
                    // - it's not a startup notification, OR
                    // - it is a startup notification AND this is the first poll
                    notification.schedule.type != NotificationScheduleType.STARTUP || isStartupPoll
                }
                ?.filter { notification ->
                    !NotificationDismissalState.getInstance().isDismissed(notification.id)
                }
                .orEmpty()

            activeNotifications.forEach { processNotification(project, it) }
        }
        LOG.info { "Finished processing notifications" }
    }

    fun processNotification(project: Project, notificationData: NotificationData) {
        val shouldShow = RulesEngine.displayNotification(project, notificationData)
        if (shouldShow) {
            LOG.info { "Showing notification with id: ${notificationData.id}" }
            val notificationContent = notificationData.content.locale
            val severity = checkSeverity(notificationData.severity)
            val followupActions = NotificationManager.createActions(
                project,
                notificationData.actions,
                notificationContent.description,
                notificationContent.title
            )
            showToast(
                notificationContent.title,
                notificationContent.description,
                NotificationManager.buildNotificationActions(followupActions),
                severity,
                notificationData.id
            )
            if (severity == NotificationSeverity.CRITICAL) {
                val bannerContent = BannerContent(notificationData.id, notificationContent.title, notificationContent.description, followupActions, severity)
                BannerNotificationService.getInstance().addNotification(notificationData.id, bannerContent)
                notifyListenerForNotification(bannerContent)
            }
            ToolkitTelemetry.showNotification(
                id = notificationData.id,
                result = Result.Succeeded,
                component = Component.Infobar
            )
        }
    }

    private fun showToast(title: String, message: String, action: List<AnAction>, notificationType: NotificationSeverity, notificationId: String) {
        val notifyType = when (notificationType) {
            NotificationSeverity.CRITICAL -> NotificationType.ERROR
            NotificationSeverity.WARNING -> NotificationType.WARNING
            NotificationSeverity.INFO -> NotificationType.INFORMATION
        }
        notifyStickyWithData(notifyType, title, message, null, action, notificationId)
    }

    fun notifyListenerForNotification(bannerContent: BannerContent) =
        notifListener.forEach { it(bannerContent) }

    fun addListenerForNotification(newNotifListener: NotifListener) =
        notifListener.add(newNotifListener)

    companion object {
        private val LOG = getLogger<ProcessNotificationsBase>()
        fun getInstance(project: Project): ProcessNotificationsBase = project.service()
    }
}

typealias NotifListener = (bannerContent: BannerContent) -> Unit
