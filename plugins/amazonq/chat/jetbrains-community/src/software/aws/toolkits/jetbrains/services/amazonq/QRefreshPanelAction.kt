// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow

class QRefreshPanelAction : DumbAwareAction("Refresh Q ToolWindow", null, AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // recreate chat browser
        AmazonQToolWindow.getInstance(project).disposeAndRecreate()

        // recreate signin browser
        QWebviewPanel.getInstance(project).disposeAndRecreate()

        BearerTokenProviderListener.notifyCredUpdate("refresh")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isDeveloperMode()
    }
}
