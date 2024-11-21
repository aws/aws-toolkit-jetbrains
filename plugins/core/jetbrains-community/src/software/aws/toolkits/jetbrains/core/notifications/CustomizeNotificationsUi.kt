// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.resources.AwsCoreBundle

fun checkSeverity(notificationSeverity: String): NotificationSeverity = when (notificationSeverity) {
    "Critical" -> NotificationSeverity.CRITICAL
    "Warning" -> NotificationSeverity.WARNING
    "Info" -> NotificationSeverity.INFO
    else -> NotificationSeverity.INFO
}

object NotificationManager {
    fun createActions(
        project: Project,
        followupActions: List<NotificationFollowupActions>?,
        message: String,
        title: String,

    ): List<NotificationActionList> = buildList {
        var url: String? = null
        followupActions?.forEach { action ->
            if (action.type == "ShowUrl") {
                url = action.content.locale.url
            }

            if (action.type == "UpdateExtension") {
                add(
                    NotificationActionList(AwsCoreBundle.message("notification.update")) {
                        // TODO: Add update logic
                    }
                )
            }

            if (action.type == "OpenChangelog") {
                add(
                    NotificationActionList(AwsCoreBundle.message("notification.changelog")) {
                        BrowserUtil.browse(AwsToolkit.GITHUB_CHANGELOG)
                    }
                )
            }
        }
        add(
            NotificationActionList(AwsCoreBundle.message("general.more_dialog")) {
                if (url == null) {
                    Messages.showYesNoDialog(
                        project,
                        message,
                        title,
                        AwsCoreBundle.message("general.acknowledge"),
                        AwsCoreBundle.message("general.cancel"),
                        AllIcons.General.Error
                    )
                } else {
                    val openLink = Messages.showYesNoDialog(
                        project,
                        message,
                        title,
                        AwsCoreBundle.message(AwsCoreBundle.message("notification.learn_more")),
                        AwsCoreBundle.message("general.cancel"),
                        AllIcons.General.Error
                    )
                    if (openLink == 0) {
                        BrowserUtil.browse(url!!)
                    }
                }
            }
        )
    }

    fun buildNotificationActions(actions: List<NotificationActionList>): List<AnAction> = actions.map { (title, block) ->
        object : AnAction(title) {
            override fun actionPerformed(e: AnActionEvent) {
                block()
            }
        }
    }

    fun buildBannerPanel(panel: EditorNotificationPanel, actions: List<NotificationActionList>): EditorNotificationPanel {
        actions.forEach { (actionTitle, block) ->
            panel.createActionLabel(actionTitle) {
                block()
            }
        }

        return panel
    }
}

data class NotificationActionList(
    val title: String,
    val blockToExecute: () -> Unit,
)

data class BannerContent(
    val title: String,
    val message: String,
    val actions: List<NotificationActionList>,
    val id: String,
)
