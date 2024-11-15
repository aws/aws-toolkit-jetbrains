// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

fun checkSeverity(notificationSeverity: String): NotificationSeverity = when (notificationSeverity) {
    "Critical" -> NotificationSeverity.CRITICAL
    "Warning" -> NotificationSeverity.WARNING
    "Info" -> NotificationSeverity.INFO
    else -> NotificationSeverity.INFO
}

// TODO: Add actions that can be performed from the notifications here
