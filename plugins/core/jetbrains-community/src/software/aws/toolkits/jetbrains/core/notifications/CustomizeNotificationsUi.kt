// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel

fun checkSeverity(notificationSeverity: String): NotificationSeverity = when (notificationSeverity) {
    "Critical" -> NotificationSeverity.CRITICAL
    "Warning" -> NotificationSeverity.WARNING
    "Info" -> NotificationSeverity.INFO
    else -> NotificationSeverity.INFO
}

fun getNotificationActionList(
    notificationFollowupActions: List<NotificationFollowupActions>?,
    notificationTitle: String,
    notificationMessage: String,
): List<AnAction> {
    val actionList = mutableListOf<AnAction>()
    actionList.add(
        getNotifAction("Expand") {
            Messages.showYesNoDialog(null, notificationMessage, notificationTitle, "OK", "Cancel", AllIcons.General.Error)
        }
    )
    if (notificationFollowupActions.isNullOrEmpty()) return actionList
    notificationFollowupActions.forEach { notificationAction ->
        if (notificationAction.type == "ShowUrl") {
            actionList.add(
                getNotifAction("Learn more") {
                    notificationAction.content.locale.url?.let { url -> BrowserUtil.browse(url) }
                }
            )
        }

        if (notificationAction.type == "UpdateExtension") {
            actionList.add(
                getNotifAction("Update") {
                    // add update logic
                }
            )
        }

        if (notificationAction.type == "openChangelog") {
            actionList.add(
                getNotifAction("Changelog") {
                    BrowserUtil.browse("https://github.com/aws/aws-toolkit-jetbrains/blob/main/CHANGELOG.md")
                }
            )
        }
    }
    return actionList
}

fun getNotifAction(title: String, block: () -> Unit): AnAction = object : AnAction(title) {
    override fun actionPerformed(e: AnActionEvent) {
        block()
    }
}

fun getBannerActionList(
    notificationFollowupActions: List<NotificationFollowupActions>?,
    notificationTitle: String,
    notificationMessage: String,
): EditorNotificationPanel {
    val panel = EditorNotificationPanel()

    panel.text = notificationTitle
    panel.icon(AllIcons.General.Error)
    panel.createActionLabel("Expand") {
        Messages.showYesNoDialog(null, notificationMessage, notificationTitle, "OK", "Cancel", AllIcons.General.Error)
    }
    if (notificationFollowupActions.isNullOrEmpty()) return panel
    notificationFollowupActions.forEach { notificationAction ->
        if (notificationAction.type == "ShowUrl") {
            panel.createActionLabel("Learn more") {
                notificationAction.content.locale.url?.let { url -> BrowserUtil.browse(url) }
            }
        }

        if (notificationAction.type == "UpdateExtension") {
            panel.createActionLabel("Update") {
                // add update logic
            }
        }

        if (notificationAction.type == "openChangelog") {
            panel.createActionLabel("Changelog") {
                BrowserUtil.browse("https://github.com/aws/aws-toolkit-jetbrains/blob/main/CHANGELOG.md")
            }
        }
    }
    return panel
}
