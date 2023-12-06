// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.explorerActions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.explorer.refreshCwQTree
import software.aws.toolkits.jetbrains.core.gettingstarted.requestCredentialsForQ
import software.aws.toolkits.jetbrains.services.amazonq.gettingstarted.QGettingStartedVirtualFile
import software.aws.toolkits.jetbrains.settings.MeetQSettings
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.UiTelemetry

class SignInToQAction : SignInToQActionBase(message("q.sign.in"))

class EnableQAction : SignInToQActionBase(message("q.enable.text"))

abstract class SignInToQActionBase(actionName: String) : DumbAwareAction(actionName, null, AllIcons.CodeWithMe.CwmAccess) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        UiTelemetry.click(project, "auth_start_Q")
        val connectionManager = ToolkitConnectionManager.getInstance(project)
        connectionManager.activeConnectionForFeature(QConnection.getInstance())?.let {
            project.refreshCwQTree()
            reauthConnectionIfNeeded(project, it)
        } ?: run {
            runInEdt {
                if (requestCredentialsForQ(project)) {
                    project.refreshCwQTree()
                    val meetQSettings = MeetQSettings.getInstance()
                    if (!meetQSettings.shouldDisplayPage) {
                        return@runInEdt
                    } else {
                        FileEditorManager.getInstance(
                            project
                        ).openTextEditor(
                            OpenFileDescriptor(
                                project,
                                QGettingStartedVirtualFile()
                            ),
                            true
                        )
                        meetQSettings.shouldDisplayPage = false
                        UiTelemetry.click(project, "toolkit_openedWelcomeToAmazonQPage")
                    }
                }
            }
        }
    }
}
