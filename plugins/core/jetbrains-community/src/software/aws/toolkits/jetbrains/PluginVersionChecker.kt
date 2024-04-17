// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import kotlinx.coroutines.CoroutineScope

class PluginVersionChecker : ApplicationInitializedListener {
    override suspend fun execute(asyncScope: CoroutineScope) {
        val notificationGroup = SingletonNotificationManager("aws.plugin.version.mismatch", NotificationType.WARNING)
        notificationGroup.notify("plugin version mismatch", "something", null) {
            it.isImportant = true
            it.addAction(
                NotificationAction.createSimpleExpiring("action") {
                }
            )
        }
    }
}
