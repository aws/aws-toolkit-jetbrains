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

class PauseServiceAction :
    SingleResourceNodeAction<AppRunnerServiceNode>(message("apprunner.action.pause")),
    DumbAware,
    CoroutineScope by ApplicationThreadPoolScope("PauseServiceAction") {
    override fun update(selected: AppRunnerServiceNode, e: AnActionEvent) {
        e.presentation.isVisible = selected.service.status() == ServiceStatus.RUNNING
    }

    override fun actionPerformed(selected: AppRunnerServiceNode, e: AnActionEvent) {
        val answer = Messages.showOkCancelDialog(
            selected.nodeProject,
            message("apprunner.pause.warning", selected.service.serviceName()),
            message("apprunner.action.pause"),
            message("apprunner.action.pause.confirm"),
            Messages.getCancelButton(),
            Messages.getWarningIcon()
        )
        if (answer == Messages.OK) {
            launch {
                performPause(selected.nodeProject, selected.nodeProject.awsClient(), selected.service)
            }
        } else {
            ApprunnerTelemetry.pauseService(selected.nodeProject, Result.Cancelled)
        }
    }

    internal fun performPause(project: Project, client: AppRunnerClient, service: ServiceSummary) = try {
        client.pauseService { it.serviceArn(service.serviceArn()) }
        notifyInfo(
            project = project,
            title = message("apprunner.action.pause"),
            content = message("apprunner.pause.succeeded", service.serviceName())
        )
        ApprunnerTelemetry.pauseService(project, Result.Succeeded)
    } catch (e: Exception) {
        val m = if (e is AppRunnerException) {
            e.awsErrorDetails()?.errorMessage() ?: ""
        } else {
            ""
        }
        notifyError(
            title = message("apprunner.action.pause"),
            project = project,
            content = message("apprunner.pause.failed", service.serviceName(), m)
        )
        LOG.error(e) { "Exception thrown while pausing AppRunner service" }
        ApprunnerTelemetry.pauseService(project, Result.Failed)
    } finally {
        project.refreshAwsTree(AppRunnerResources.LIST_SERVICES)
    }

    private companion object {
        val LOG = getLogger<PauseServiceAction>()
    }
}
