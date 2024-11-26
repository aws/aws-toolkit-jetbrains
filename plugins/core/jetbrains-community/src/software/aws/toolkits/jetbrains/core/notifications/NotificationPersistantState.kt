// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "notificationDismissals", storages = [Storage("aws.xml")])
class NotificationDismissalState : PersistentStateComponent<NotificationDismissalConfiguration> {
    private var state = NotificationDismissalConfiguration()

    override fun getState(): NotificationDismissalConfiguration = state

    override fun loadState(state: NotificationDismissalConfiguration) {
        this.state = state
    }

    fun isDismissed(notificationId: String): Boolean =
        state.dismissedNotificationIds.contains(notificationId)

    fun dismissNotification(notificationId: String) {
        state.dismissedNotificationIds.add(notificationId)
    }

    companion object {
        fun getInstance(): NotificationDismissalState =
            ApplicationManager.getApplication().getService(NotificationDismissalState::class.java)
    }
}

data class NotificationDismissalConfiguration(
    var dismissedNotificationIds: MutableSet<String> = mutableSetOf(),
)
