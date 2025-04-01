// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.profile.QProfileSwitchIntent
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileDialog
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.resources.AmazonQBundle.message
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry

class QSwitchProfilesAction : AnAction(message("action.q.switchProfiles.text")), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.icon = AllIcons.Actions.SwapPanels
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val profiles = try {
                QRegionProfileManager.getInstance().listRegionProfiles(project)
            } catch (e: Exception) {
                val conn = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
                Telemetry.amazonq.didSelectProfile.use { span ->
                    span.source(QProfileSwitchIntent.User.value)
                        .amazonQProfileRegion(QRegionProfileManager.getInstance().activeProfile(project)?.region ?: "not-set")
                        .ssoRegion(conn?.region)
                        .credentialStartUrl(conn?.startUrl)
                        .result(MetricResult.Failed)
                        .reason(e.message)
                }
                throw e
            }
                ?: error("Attempted to fetch profiles while there does not exist")
            val selectedProfile = QRegionProfileManager.getInstance().activeProfile(project) ?: profiles[0]
            ApplicationManager.getApplication().invokeLater {
                QRegionProfileDialog(
                    project,
                    profiles = profiles,
                    selectedProfile = selectedProfile
                ).show()
            }
        }
    }
}
