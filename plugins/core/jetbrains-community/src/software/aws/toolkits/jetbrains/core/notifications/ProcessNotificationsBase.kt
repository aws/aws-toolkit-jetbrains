// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.utils.notifyStickyWithData

@Service(Service.Level.PROJECT)
class ProcessNotificationsBase {
    private val notifListener = mutableListOf<NotifListener>()
    init {
        // TODO: install a listener for the polling class
    }

    fun getNotificationsFromFile() {
        // TODO: returns a notification list
    }

    fun retrieveStartupAndEmergencyNotifications() {
        // TODO: separates notifications into startup and emergency
        // iterates through the 2 lists and processes each notification(if it isn't dismissed)
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
    }
}

typealias NotifListener = (bannerContent: BannerContent) -> Unit
