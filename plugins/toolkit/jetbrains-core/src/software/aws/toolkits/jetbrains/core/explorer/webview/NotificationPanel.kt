// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.webview

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.jetbrains.core.notifications.BannerContent
import software.aws.toolkits.jetbrains.core.notifications.NotificationManager
import software.aws.toolkits.jetbrains.core.notifications.ProcessNotificationsBase
import software.aws.toolkits.resources.AwsCoreBundle

class NotificationPanel : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
        // will show only 1 critical notification
        ProcessNotificationsBase.showBannerNotification.forEach {
            updateNotificationPanel(it.value)
        }
    }

    private fun removeNotificationPanel(notificationId: String) = runInEdt {
        ProcessNotificationsBase.showBannerNotification.remove(notificationId) // TODO: add id to dismissed notification list
        wrapper.removeAll()
    }

    fun updateNotificationPanel(bannerContent: BannerContent) {
        val panel = EditorNotificationPanel()
        panel.text = bannerContent.title
        panel.icon(AllIcons.General.Error)
        val panelWithActions = NotificationManager.buildBannerPanel(panel, bannerContent.actions)

        panelWithActions.createActionLabel(AwsCoreBundle.message("general.dismiss")) {
            removeNotificationPanel(bannerContent.id)
        }

        wrapper.setContent(panelWithActions)
    }
}
