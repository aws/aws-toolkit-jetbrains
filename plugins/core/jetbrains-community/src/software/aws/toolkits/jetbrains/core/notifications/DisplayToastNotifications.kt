// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import software.aws.toolkits.jetbrains.utils.notifySticky

object DisplayToastNotifications {
    fun show(title: String, message: String, action: List<AnAction>, notificationType: ToastNotificationType) {
        val notifyType = when (notificationType) {
            ToastNotificationType.CRITICAL -> NotificationType.ERROR
            ToastNotificationType.WARNING -> NotificationType.WARNING
            ToastNotificationType.PRODUCT_UPDATE -> NotificationType.INFORMATION
        }
        notifySticky(notifyType, title, message, null, action)
    }
}

enum class ToastNotificationType {
    CRITICAL,
    WARNING,
    PRODUCT_UPDATE,
}
