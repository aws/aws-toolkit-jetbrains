// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.utils.notifyStickyWithData

object DisplayToastNotifications {
    fun showToast(title: String, message: String, action: List<AnAction>, notificationType: NotificationSeverity, notificationId: String) {
        val notifyType = when (notificationType) {
            NotificationSeverity.CRITICAL -> NotificationType.ERROR
            NotificationSeverity.WARNING -> NotificationType.WARNING
            NotificationSeverity.INFO -> NotificationType.INFORMATION
        }
        notifyStickyWithData(notifyType, title, message, null, action, notificationId)
    }

    fun shouldShowNotification(project: Project, notificationData: NotificationData) {
        if (RulesEngine.displayNotification(notificationData, project)) {
            val notificationContent = notificationData.content.locale
            val severity = notificationData.severity
            showToast(
                notificationContent.title,
                notificationContent.description,
                getNotificationActionList(notificationData.actions, notificationContent.title, notificationContent.description),
                checkSeverity(severity),
                notificationData.id
            )

            if (severity == "Critical") {
                ShowCriticalNotificationBannerListener.showBanner(notificationContent.title, notificationContent.description, notificationData.actions)
            }
        }
    }
}
