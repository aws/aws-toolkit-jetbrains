// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
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

class EnableEcsExecuteCommand :
    SingleResourceNodeAction<EcsServiceNode>(message("ecs.execute_command_enable"), null),
    CoroutineScope by ApplicationThreadPoolScope("EnableExecuteCommand") {
    private val settings = EcsExecCommandSettings.getInstance()
    override fun actionPerformed(selected: EcsServiceNode, e: AnActionEvent) {
        if (!settings.showExecuteCommandWarning || ExecuteCommandWarning(selected.nodeProject, enable = true).showAndGet()) {
            launch {
                enableExecuteCommand(selected.nodeProject, selected.value)
            }
        }
    }

    override fun update(selected: EcsServiceNode, e: AnActionEvent) {
        e.presentation.isVisible = !EcsExecUtils(selected.nodeProject).executeCommandFlagStatus(selected.value)
    }

    private suspend fun enableExecuteCommand(project: Project, service: Service) {
        EcsExecUtils(project).updateExecuteCommandFlag(service, true)
        val serviceUpdated = EcsExecUtils(project).checkServiceState(service)
        if (serviceUpdated) {
            notifyInfo(message("ecs.execute_command_enable"), message("ecs.execute_command_enable_success", service.serviceName()))
        } else {
            notifyError(message("ecs.execute_command_enable"), message("ecs.execute_command_enable_failed", service.serviceName()))
        }
    }
}
