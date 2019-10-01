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
    fun checkAndNotify(project: Project)

    companion object {
        fun getInstance(): ChangeNotificationManager = ServiceManager.getService(ChangeNotificationManager::class.java)
    }
}

internal const val CHANGE_NOTIFICATION_GROUP_ID = "AWS Toolkit Change Notification"

@State(name = "changenotifications", storages = [Storage("aws.xml")])
class DefaultChangeNotificationManager : PersistentStateComponent<ChangeNotificationStateList>, ChangeNotificationManager {
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

    override fun checkAndNotify(project: Project) {
        ChangeType.changes().forEach {
            if (it.isNotificationRequired()) {
                internalState[it.id]?.let { state ->
                    state.notificationValue?.let { notificationValue ->
                        if (it.isNotificationSuppressed(notificationValue)) {
                            return@forEach
                        }
                    }
                }

                notify(it, project)
            }
        }
    }

    fun notify(change: ChangeType, project: Project) {
        val notification = notificationGroup.createNotification(
            change.getNotificationTitle(),
            change.getNotificationMessage(),
            NotificationType.INFORMATION
        ) { _, _ ->
            suppressNotification(change)
        }

        Notifications.Bus.notify(notification, project)
    }

    fun suppressNotification(change: ChangeType) {
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
