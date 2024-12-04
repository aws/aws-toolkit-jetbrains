// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(name = "notificationDismissals", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
class NotificationDismissalState : PersistentStateComponent<NotificationDismissalConfiguration> {
    private val state = NotificationDismissalConfiguration()

    override fun getState(): NotificationDismissalConfiguration = state

    override fun loadState(state: NotificationDismissalConfiguration) {
        this.state.dismissedNotificationIds.clear()
        this.state.dismissedNotificationIds.addAll(state.dismissedNotificationIds)
    }

    fun isDismissed(notificationId: String): Boolean =
        state.dismissedNotificationIds.contains(notificationId)

    fun dismissNotification(notificationId: String) {
        state.dismissedNotificationIds.add(notificationId)
    }

    companion object {
        fun getInstance(): NotificationDismissalState =
            service()
    }
}

data class NotificationDismissalConfiguration(
    var dismissedNotificationIds: MutableSet<String> = mutableSetOf(),
)

@Service
@State(name = "notificationEtag", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
class NotificationEtagState : PersistentStateComponent<NotificationEtagConfiguration> {
    private val state = NotificationEtagConfiguration()

    override fun getState(): NotificationEtagConfiguration = state

    override fun loadState(state: NotificationEtagConfiguration) {
        this.state.etag = state.etag
    }

    var etag: String?
        get() = state.etag
        set(value) {
            state.etag = value
        }

    companion object {
        fun getInstance(): NotificationEtagState =
            service()
    }
}

data class NotificationEtagConfiguration(
    var etag: String? = null,
)

@Service
class BannerNotificationService {
    private val notifications = mutableMapOf<String, BannerContent>()

    fun addNotification(id: String, content: BannerContent) {
        notifications[id] = content
    }

    fun getNotifications(): Map<String, BannerContent> = notifications

    fun removeNotification(id: String) {
        notifications.remove(id)
    }

    companion object {
        fun getInstance(): BannerNotificationService =
            service()
    }
}
