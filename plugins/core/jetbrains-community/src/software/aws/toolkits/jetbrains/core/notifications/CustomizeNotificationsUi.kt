// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notifications

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel
import software.aws.toolkits.jetbrains.AwsPlugin
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
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
                        updatePlugins()
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
                    Messages.showMessageDialog(
                        project,
                        message,
                        title,
                        AllIcons.General.Error
                    )
                } else {
                    val link = url ?: AwsToolkit.GITHUB_URL
                    val openLink = Messages.showYesNoDialog(
                        project,
                        message,
                        title,
                        AwsCoreBundle.message(AwsCoreBundle.message("notification.learn_more")),
                        AwsCoreBundle.message("general.cancel"),
                        AllIcons.General.Error
                    )
                    if (openLink == 0) {
                        BrowserUtil.browse(link)
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
    private fun updatePlugins() {
        val pluginUpdateManager = PluginUpdateManager()
        runInEdt {
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                null,
                AwsCoreBundle.message("aws.settings.auto_update.progress.message")
            ) {
                override fun run(indicator: ProgressIndicator) {
                    pluginUpdateManager.checkForUpdates(indicator, AwsPlugin.TOOLKIT)
                }
            })
        }
    }
}

data class NotificationActionList(
    val title: String,
    val blockToExecute: () -> Unit,
)

data class BannerContent(
    val id: String,
    val title: String,
    val message: String,
    val actions: List<NotificationActionList> = emptyList(),
    val severity: NotificationSeverity = NotificationSeverity.INFO,
)
