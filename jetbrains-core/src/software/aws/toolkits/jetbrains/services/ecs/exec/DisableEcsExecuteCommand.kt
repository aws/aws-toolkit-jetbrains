// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.ecs.model.Service
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.ecs.EcsServiceNode
import software.aws.toolkits.jetbrains.settings.EcsExecCommandSettings
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

class DisableEcsExecuteCommand :
    SingleResourceNodeAction<EcsServiceNode>(message("ecs.execute_command_disable"), null),
    CoroutineScope by ApplicationThreadPoolScope("DisableExecuteCommand") {
    private val settings = EcsExecCommandSettings.getInstance()
    override fun actionPerformed(selected: EcsServiceNode, e: AnActionEvent) {
        if (!settings.showExecuteCommandWarning || (Messages.showYesNoCancelDialog(message("ecs.execute_command_disable_warning"), message("ecs.execute_command_disable_warning_title"), "Yes", "No", "Cancel", Messages.getWarningIcon(), ExecuteCommandWarningDoNotShow()) == 0)) {
            launch {
                disableExecuteCommand(selected.nodeProject, selected.value)
            }
        }
    }

    override fun update(selected: EcsServiceNode, e: AnActionEvent) {
        e.presentation.isVisible = EcsExecUtils(selected.nodeProject).executeCommandFlagStatus(selected.value)
    }

    private suspend fun disableExecuteCommand(project: Project, service: Service) {
        EcsExecUtils(project).updateExecuteCommandFlag(service, enabled = false)
        val serviceUpdated = EcsExecUtils(project).checkServiceState(service)
        if (serviceUpdated) {
            notifyInfo(message("ecs.execute_command_disable"), message("ecs.execute_command_disable_success", service.serviceName()))
        } else {
            notifyError(message("ecs.execute_command_disable"), message("ecs.execute_command_disable_failed", service.serviceName()))
        }
    }
}
