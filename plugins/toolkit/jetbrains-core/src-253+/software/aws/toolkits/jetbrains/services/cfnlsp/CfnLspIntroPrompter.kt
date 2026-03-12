// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.jetbrains.core.explorer.AwsToolkitExplorerToolWindow
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class CfnLspIntroPrompter : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (CfnLspIntroPromptState.getInstance().hasResponded()) return

        showPrompt(project)
    }

    private fun showPrompt(project: Project) {
        val notification = Notification(
            CfnLspExtensionConfig.INTRO_NOTIFICATION_GROUP_ID,
            message("cloudformation.lsp.intro.prompt.title"),
            message("cloudformation.lsp.intro.prompt.message"),
            NotificationType.INFORMATION
        )

        notification.addAction(object : NotificationAction(message("cloudformation.lsp.intro.prompt.action.explore")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                openCloudFormationTab(project)
                applyChoice()
                notification.expire()
            }
        })

        notification.addAction(object : NotificationAction(message("cloudformation.lsp.intro.prompt.action.dont_show")) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                applyChoice()
                notification.expire()
            }
        })

        notification.notify(project)
    }

    private fun openCloudFormationTab(project: Project) {
        val explorerToolWindow = AwsToolkitExplorerToolWindow.toolWindow(project)
        explorerToolWindow.activate(null, true)

        val cfnTabTitle = message("cloudformation.explorer.tab.title")
        AwsToolkitExplorerToolWindow.getInstance(project).selectTab(cfnTabTitle)
    }

    private fun applyChoice() {
        CfnLspIntroPromptState.getInstance().setResponded()
    }
}
