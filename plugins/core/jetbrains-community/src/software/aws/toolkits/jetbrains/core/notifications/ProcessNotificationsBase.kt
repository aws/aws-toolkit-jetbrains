// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.project.Project
import java.nio.file.Path

class ProcessNotificationsBase {
    init {
        NotificationPollingServiceImpl.getInstance().addObserver { path ->
            getNotificationsFromFile(path)
        }
    }

    fun getNotificationsFromFile(path: Path) {
        // TODO: returns a notification list
    }

    fun retrieveStartupAndEmergencyNotifications() {
        // TODO: separates notifications into startup and emergency
        // iterates through the 2 lists and processes each notification(if it isn't dismissed)
    }

    fun processNotification(project: Project, notificationData: NotificationData) {
        val shouldShow = RulesEngine.displayNotification(project, notificationData)
        if (shouldShow) {
            // TODO: notifies listeners
        }
    }

    fun notifyListenerForNotification() {
    }

    fun addListenerForNotification() {
    }
}
