// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import software.aws.toolkit.core.utils.ETagProvider
import java.time.Duration
import java.time.Instant

data class DismissedNotification(
    var id: String = "",
    var dismissedAt: String = Instant.now().toEpochMilli().toString(),
)

data class NotificationDismissalConfiguration(
    var dismissedNotifications: MutableSet<DismissedNotification> = mutableSetOf(),
)

@Service
@State(name = "toolkitNotificationDismissals", storages = [Storage("awsToolkit.xml")])
class NotificationDismissalState : PersistentStateComponent<NotificationDismissalConfiguration> {
    private var state = NotificationDismissalConfiguration()
    private val retentionPeriod = Duration.ofDays(60) // 2 months

    override fun getState(): NotificationDismissalConfiguration = state

    override fun loadState(state: NotificationDismissalConfiguration) {
        this.state = state
        cleanExpiredNotifications()
    }

    fun isDismissed(notificationId: String): Boolean =
        state.dismissedNotifications.any { it.id == notificationId }

    fun dismissNotification(notificationId: String) {
        state.dismissedNotifications.add(
            DismissedNotification(
                id = notificationId
            )
        )
    }

    private fun cleanExpiredNotifications() {
        val now = Instant.now()
        state.dismissedNotifications.removeAll { notification ->
            Duration.between(Instant.ofEpochMilli(notification.dismissedAt.toLong()), now) > retentionPeriod
        }
    }

    companion object {
        fun getInstance(): NotificationDismissalState = service()
    }
}

@Service
@State(name = "toolkitNotificationEtag", storages = [Storage("awsToolkit.xml")])
class NotificationEtagState : PersistentStateComponent<NotificationEtagConfiguration>, ETagProvider {
    private val state = NotificationEtagConfiguration()

    override fun updateETag(newETag: String?) {
        etag = newETag
    }

    override fun getState(): NotificationEtagConfiguration = state

    override fun loadState(state: NotificationEtagConfiguration) {
        this.state.etag = state.etag
    }

    override var etag: String?
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
