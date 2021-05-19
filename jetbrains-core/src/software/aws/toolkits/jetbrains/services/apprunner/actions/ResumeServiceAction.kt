// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.apprunner.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.apprunner.AppRunnerClient
import software.amazon.awssdk.services.apprunner.model.AppRunnerException
import software.amazon.awssdk.services.apprunner.model.ServiceStatus
import software.amazon.awssdk.services.apprunner.model.ServiceSummary
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import software.aws.toolkits.jetbrains.services.apprunner.AppRunnerServiceNode
import software.aws.toolkits.jetbrains.services.apprunner.resources.AppRunnerResources
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.ApprunnerTelemetry
import software.aws.toolkits.telemetry.Result

class ResumeServiceAction :
    SingleResourceNodeAction<AppRunnerServiceNode>(message("apprunner.action.resume")),
    DumbAware,
    CoroutineScope by ApplicationThreadPoolScope("ResumeServiceAction") {
    override fun update(selected: AppRunnerServiceNode, e: AnActionEvent) {
        e.presentation.isVisible = selected.service.status() == ServiceStatus.PAUSED
    }

    // TODO add help links
    override fun actionPerformed(selected: AppRunnerServiceNode, e: AnActionEvent) {
        val answer = Messages.showYesNoDialog(
            selected.nodeProject,
            message("apprunner.resume.warning", selected.service.serviceName()),
            message("apprunner.action.resume"),
            Messages.getWarningIcon()
        )
        if (answer == Messages.YES) {
            launch {
                performResume(selected.nodeProject, selected.nodeProject.awsClient(), selected.service)
            }
        } else {
            ApprunnerTelemetry.pauseService(selected.nodeProject, Result.Cancelled)
        }
    }

    internal fun performResume(project: Project, client: AppRunnerClient, service: ServiceSummary) = try {
        client.resumeService { it.serviceArn(service.serviceArn()) }
        notifyInfo(
            project = project,
            title = message("apprunner.action.resume"),
            content = message("apprunner.resume.succeeded", service.serviceName())
        )
        project.refreshAwsTree(AppRunnerResources.LIST_SERVICES)
        ApprunnerTelemetry.resumeService(project, Result.Succeeded)
    } catch (e: Exception) {
        val m = if (e is AppRunnerException) {
            e.awsErrorDetails()?.errorMessage() ?: ""
        } else {
            ""
        }
        notifyError(
            project = project,
            title = message("apprunner.action.resume"),
            content = message("apprunner.resume.failed", service.serviceName(), m)
        )
        LOG.error(e) { "Exception thrown while resuming AppRunner service" }
        ApprunnerTelemetry.resumeService(project, Result.Failed)
    } finally {
        project.refreshAwsTree(AppRunnerResources.LIST_SERVICES)
    }

    private companion object {
        val LOG = getLogger<ResumeServiceAction>()
    }
}
