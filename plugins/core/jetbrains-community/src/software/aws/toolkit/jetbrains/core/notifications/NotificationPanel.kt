// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.notifications

import com.intellij.openapi.application.runInEdt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel

class NotificationPanel : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
        BannerNotificationService.getInstance().getNotifications().forEach { (_, content) ->
            updateNotificationPanel(content)
        }
    }

    private fun removeNotificationPanel(notificationId: String) = runInEdt {
        BannerNotificationService.getInstance().removeNotification(notificationId)
        NotificationDismissalState.getInstance().dismissNotification(notificationId)
        wrapper.setContent(null)
    }

    fun updateNotificationPanel(bannerContent: BannerContent) {
        val panel = EditorNotificationPanel(
            when (bannerContent.severity) {
                NotificationSeverity.CRITICAL -> EditorNotificationPanel.Status.Error
                NotificationSeverity.WARNING -> EditorNotificationPanel.Status.Warning
                NotificationSeverity.INFO -> EditorNotificationPanel.Status.Info
            }
        )
        panel.text = bannerContent.title

        val panelWithActions = NotificationManager.buildBannerPanel(panel, bannerContent.actions)
        panelWithActions.setCloseAction {
            removeNotificationPanel(bannerContent.id)
        }

        wrapper.setContent(panelWithActions)
    }
}
