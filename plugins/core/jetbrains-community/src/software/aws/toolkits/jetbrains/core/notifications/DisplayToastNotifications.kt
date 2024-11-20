// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.project.Project

object DisplayToastNotifications {
//    fun show(title: String, message: String, action: List<AnAction>, notificationType: NotificationSeverity) {
//        val notifyType = when (notificationType) {
//            NotificationSeverity.CRITICAL -> NotificationType.ERROR
//            NotificationSeverity.WARNING -> NotificationType.WARNING
//            NotificationSeverity.INFO -> NotificationType.INFORMATION
//        }
//    }

    fun shouldShow(project: Project, notificationData: NotificationData) {
        if (RulesEngine.displayNotification(notificationData, project)) {
            val notificationContent = notificationData.content.locale
            val severity = notificationData.severity
            notificationContent
            severity
            // show(notificationContent.title, notificationContent.description, emptyList(), checkSeverity(severity))
        }
    }
}
