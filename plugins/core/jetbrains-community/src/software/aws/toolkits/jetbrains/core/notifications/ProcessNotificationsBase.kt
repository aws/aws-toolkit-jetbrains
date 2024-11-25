// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.utils.inputStream
import software.aws.toolkits.jetbrains.utils.notifyStickyWithData
import java.nio.file.Paths

object NotificationMapperUtil {
    val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@Service(Service.Level.PROJECT)
class ProcessNotificationsBase {
    private val notifListener = mutableListOf<NotifListener>()
    init {
        NotificationPollingService.getInstance().addObserver {
            retrieveStartupAndEmergencyNotifications()
        }
    }

    private fun getNotificationsFromFile(): NotificationsList? {
        val path = Paths.get(PathManager.getSystemPath(), NOTIFICATIONS_PATH)
        val content = path.inputStream().bufferedReader().use { it.readText() }
        if (content.isEmpty()) {
            return null
        }
        return NotificationMapperUtil.mapper.readValue(content)
    }

    fun retrieveStartupAndEmergencyNotifications() {
        val notifications = getNotificationsFromFile()

        notifications?.let { notificationsList ->
            val (startupNotifications, emergencyNotifications) = notificationsList.notifications
                ?.partition { notification ->
                    notification.schedule.type.equals("StartUp", ignoreCase = true)
                }
                ?: Pair(emptyList(), emptyList())


            val startupNotificationsList = NotificationsList(
                schema = notificationsList.schema,
                notifications = startupNotifications
            )

            val emergencyNotificationsList = NotificationsList(
                schema = notificationsList.schema,
                notifications = emergencyNotifications
            )

            // Now you can process each list separately
            // TODO: Process the separated lists as needed
        }
    }

    fun processNotification(project: Project, notificationData: NotificationData) {
        val shouldShow = RulesEngine.displayNotification(project, notificationData)
        if (shouldShow) {
            val notificationContent = notificationData.content.locale
            val severity = notificationData.severity
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
                checkSeverity(severity),
                notificationData.id
            )
            if (severity == "Critical") {
                val bannerContent = BannerContent(notificationContent.title, notificationContent.description, followupActions, notificationData.id)
                showBannerNotification[notificationData.id] = bannerContent
                notifyListenerForNotification(bannerContent)
            }
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
        fun getInstance(project: Project): ProcessNotificationsBase = project.service()

        val showBannerNotification = mutableMapOf<String, BannerContent>()
        private const val NOTIFICATIONS_PATH = "aws-static-resources/notifications.json"
    }
}

typealias NotifListener = (bannerContent: BannerContent) -> Unit
