// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.webview

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import software.aws.toolkits.jetbrains.core.notifications.NotificationActionList
import software.aws.toolkits.jetbrains.core.notifications.NotificationManager
import software.aws.toolkits.resources.AwsCoreBundle

@Service(Service.Level.PROJECT)
class NotificationPanel : BorderLayoutPanel() {
    private val wrapper = Wrapper()
    init {
        isOpaque = false
        addToCenter(wrapper)
    }

    private fun removeNotificationPanel() = runInEdt {
        wrapper.removeAll()
    }

    fun updateNotificationPanel(title: String, message: String, notificationActionList: List<NotificationActionList>) {
        val panel = EditorNotificationPanel()
        panel.text = title
        panel.icon(AllIcons.General.Error)
        val panelWithActions = NotificationManager.buildBannerPanel(panel, notificationActionList)

        panelWithActions.createActionLabel(AwsCoreBundle.message("general.dismiss")) {
            removeNotificationPanel()
        }

        wrapper.setContent(panelWithActions)
    }

    companion object {
        fun getInstance(project: com.intellij.openapi.project.Project): NotificationPanel = project.service()
    }
}
