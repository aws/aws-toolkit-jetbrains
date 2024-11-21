// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry

class DummyNotificationAction : AnAction("Show notif") {
    override fun actionPerformed(e: AnActionEvent) {
        if (!Registry.`is`("aws.toolkit.developerMode")) return
//        ShowCriticalNotificationBannerListener.showBanner(
//            "hello hello",
//            "This is a bug",
//            NotificationManager.createActions(emptyList(), "This is a bug", "hello hello")
//        )
        val a = e.project?.let { ProcessNotificationsBase.getInstance(it) } ?: return
        a.notifyListenerForNotification(
            "hello hello",
            "This is a bug",
            NotificationManager.createActions(emptyList(), "This is a bug", "hello hello")

        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = Registry.`is`("aws.toolkit.developerMode")
    }
}
