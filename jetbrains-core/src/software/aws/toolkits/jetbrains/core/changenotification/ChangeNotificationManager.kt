// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.changenotification

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

interface ChangeNotificationManager {
    fun getRequiredNotices(notices: List<ChangeType>, project: Project): List<ChangeType>
    fun notify(notices: List<ChangeType>, project: Project)

    companion object {
        fun getInstance(): ChangeNotificationManager = ServiceManager.getService(ChangeNotificationManager::class.java)
    }
}

internal const val CHANGE_NOTIFICATION_GROUP_ID = "AWS Toolkit Change Notification"

@State(name = "changenotifications", storages = [Storage("aws.xml")])
class DefaultChangeNotificationManager : PersistentStateComponent<ChangeNotificationStateList>,
    ChangeNotificationManager {
    private val internalState = mutableMapOf<String, ChangeNotificationState>()
    private val notificationGroup = NotificationGroup(CHANGE_NOTIFICATION_GROUP_ID, NotificationDisplayType.STICKY_BALLOON, true)

    override fun getState(): ChangeNotificationStateList = ChangeNotificationStateList(internalState.values.map { it }.toList())

    override fun loadState(state: ChangeNotificationStateList) {
        internalState.clear()
        state.value.forEach {
            val id = it.id ?: return@forEach
            internalState[id] = it
        }
    }

    /**
     * Returns the notices that require notification
     */
    override fun getRequiredNotices(notices: List<ChangeType>, project: Project): List<ChangeType> {
        return notices.filter { it.isNotificationRequired() }
            .filter {
                internalState[it.id]?.let { state ->
                    state.notificationValue?.let { notificationValue ->
                        return@filter !it.isNotificationSuppressed(notificationValue)

                    }
                }

                true
            }
    }

    override fun notify(notices: List<ChangeType>, project: Project) {
        notices.forEach { notify(it, project) }
    }

    private fun notify(change: ChangeType, project: Project) {
        val notification = notificationGroup.createNotification(
            change.getNoticeContents().title,
            change.getNoticeContents().message,
            NotificationType.INFORMATION
        ) { _, _ ->
            suppressNotification(change)
        }

        Notifications.Bus.notify(notification, project)
    }

    private fun suppressNotification(change: ChangeType) {
        internalState[change.id] = ChangeNotificationState(change.id, change.getNotificationValue())
    }

    fun resetAllNotifications() {
        internalState.clear()
    }
}

data class ChangeNotificationStateList(var value: List<ChangeNotificationState> = listOf())

data class ChangeNotificationState(
    var id: String? = null,
    var notificationValue: String? = null
)
