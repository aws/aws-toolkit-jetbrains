// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import java.util.EventListener

interface ShowCriticalNotificationBannerListener : EventListener {
    fun onReceiveEmergencyNotification(title: String, message: String, actions: List<NotificationFollowupActions>?) {}

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("Show critical banner", ShowCriticalNotificationBannerListener::class.java)

        fun showBanner(title: String, message: String, actions: List<NotificationFollowupActions>?) {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).onReceiveEmergencyNotification(title, message, actions)
        }
    }
}
