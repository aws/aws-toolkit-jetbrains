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
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.ui.ResourceSelector
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.EcsExecuteCommandType
import software.aws.toolkits.telemetry.EcsTelemetry
import software.aws.toolkits.telemetry.Result
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.plaf.basic.BasicComboBoxEditor

class RunCommandDialog(private val project: Project, private val container: ContainerDetails) : DialogWrapper(project) {
    private val tasks = ResourceSelector
        .builder()
        .resource(
            EcsResources.listTasks(
                container.service.clusterArn(),
                container.service.serviceArn()
            )
        )
        .awsConnection(project)
        .build()
    private val commandList = CollectionComboBoxModel(commandsEnteredPreviously.toMutableList())
    var command = ""
    private val component by lazy {
        panel {
            row(message("ecs.execute_command_task.label")) {
                tasks(growX, pushX).growPolicy(GrowPolicy.MEDIUM_TEXT)
                    .withErrorOnApplyIf(message("ecs.execute_command_task_comboBox_empty")) { it.item.isNullOrEmpty() }
            }
            row(message("ecs.execute_command.label")) {
                comboBox(
                    commandList, { command },
                    {
                        if (it != null) {
                            command = it
                        }
                    }
                ).constraints(grow)
                    .withErrorOnApplyIf(message("ecs.execute_command_no_command")) { it.editor.item.toString().isNullOrBlank() }
                    .also {
                        it.component.isEditable = true
                        it.component.selectedIndex = -1
                    }.applyToComponent {
                        editor = object : BasicComboBoxEditor.UIResource() {
                            override fun createEditorComponent(): JTextField {
                                val noCommandEntered = JBTextField()
                                noCommandEntered.emptyText.text = message("ecs.execute_command_run_command_default_text")
                                return noCommandEntered
                            }
                        }
                    }
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
        commandsEnteredPreviously.add(command)
        runCommand()
    }

    override fun doCancelAction() {
        super.doCancelAction()
        EcsTelemetry.runExecuteCommand(project, Result.Cancelled, EcsExecuteCommandType.Command)
    }

    fun constructExecCommandParameters(commandToExecute: String) =
        tasks.selected()?.let {
            message(
                "ecs.execute_command_parameters",
                container.service.clusterArn(),
                it,
                commandToExecute,
                container.containerDefinition.name()
            )
        }

    private fun runCommand() {
        try {
            val awsCliPath = AwsCliExecutable().resolve() ?: throw IllegalStateException(message("executableCommon.missing_executable", "AWS CLI"))
            val execCommand = buildExecCommandConfiguration(('"' + command + '"'), awsCliPath)
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
            EcsTelemetry.runExecuteCommand(project, Result.Succeeded, EcsExecuteCommandType.Command)
        } catch (e: Exception) {
            EcsTelemetry.runExecuteCommand(project, Result.Failed, EcsExecuteCommandType.Command)
        }
    }

    fun buildExecCommandConfiguration(commandToExecute: String, path: Path): Tool {
        val execCommand = Tool()
        execCommand.isShowConsoleOnStdOut = true
        execCommand.isUseConsole = true
        execCommand.parameters = constructExecCommandParameters(commandToExecute)
        execCommand.name = container.containerDefinition.name()
        execCommand.program = path.toAbsolutePath().toString()
        return execCommand
    }

    companion object {
        val commandsEnteredPreviously = mutableSetOf<String>()
    }
}
