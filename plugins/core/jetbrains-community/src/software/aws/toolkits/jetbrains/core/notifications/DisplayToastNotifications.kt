// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.utils.notifySticky

object DisplayToastNotifications {
    fun show(title: String, message: String, action: List<AnAction>, notificationType: NotificationSeverity) {
        val notifyType = when (notificationType) {
            NotificationSeverity.CRITICAL -> NotificationType.ERROR
            NotificationSeverity.WARNING -> NotificationType.WARNING
            NotificationSeverity.INFO -> NotificationType.INFORMATION
        }
        notifySticky(notifyType, title, message, null, action)
    }

    fun shouldShow(project: Project, notificationData: NotificationData) {
        if (RulesEngine.displayNotification(notificationData, project)) {
            val notificationContent = notificationData.content.locale
            val severity = notificationData.severity
            show(notificationContent.title, notificationContent.description, emptyList(), checkSeverity(severity))
        }
    }
}
