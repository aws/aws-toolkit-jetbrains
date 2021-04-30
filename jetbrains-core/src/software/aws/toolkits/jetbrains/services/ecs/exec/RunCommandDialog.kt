// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.tools.Tool
import com.intellij.tools.ToolRunProfile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.utils.ui.selected
import software.aws.toolkits.resources.message
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class RunCommandDialog(private val project: Project, private val container: ContainerDetails) : DialogWrapper(project) {
    private val resourceCache = AwsResourceCache.getInstance()
    private val tasks = resourceCache.getResourceNow(
        EcsResources.listTasks(
            container.service.clusterArn(),
            container.service.serviceArn()
        ),
        project.activeRegion(),
        project.activeCredentialProvider(),
        useStale = false,
        forceFetch = true
    )

    private val taskList = DefaultComboBoxModel(tasks.toTypedArray())
    var task = if (tasks.isNotEmpty()) tasks.first() else null
    private val command = JBTextField()
    private val component by lazy {
        panel {
            row(message("ecs.execute_command_task")) {
                comboBox(taskList, { task }, { if (it != null) { task = it } })
                    .constraints(growX)
                    .withErrorOnApplyIf(message("ecs.execute_command_task_comboBox_empty")) { it.selected() == null }
            }
            row(message("ecs.execute_command_label")) {
                command(grow).withErrorOnApplyIf(message("ecs.execute_command_no_command")) { it.text == "" }
            }
        }
    }
    init {
        super.init()
        title = message("ecs.execute_command_run")
        setOKButtonText(message("general.execute_button"))
    }
    override fun createCenterPanel(): JComponent? = component

    override fun doOKAction() {
        super.doOKAction()
        runCommand()
    }

    fun constructExecCommandParameters(commandToExecute: String) = task?.let {
        message(
            "ecs.execute_command_parameters",
            container.service.clusterArn(),
            it,
            commandToExecute
        )
    }

    private fun runCommand() {
        val execCommand = Tool()
        updateExecCommandRunSettings(execCommand, command.text)
        val environment = ExecutionEnvironmentBuilder
            .create(
                project,
                DefaultRunExecutor.getRunExecutorInstance(),
                ToolRunProfile(
                    execCommand,
                    SimpleDataContext.getProjectContext(project)
                )
            )
            .build()
        environment.runner.execute(environment)
    }

    fun updateExecCommandRunSettings(execCommand: Tool, commandToExecute: String) {
        execCommand.isShowConsoleOnStdOut = true
        execCommand.isUseConsole = true
        execCommand.parameters = constructExecCommandParameters(commandToExecute)
        execCommand.name = container.containerDefinition.name()
        execCommand.program = path
    }

    companion object {
        var path = ""
    }
}
