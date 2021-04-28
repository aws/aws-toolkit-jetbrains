// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.pty4j.PtyProcess
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
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
        constructExecCommand(command.text)
    }

    private fun constructExecCommand(commandToExecute: String) {
        ExecutableManager.getInstance().getExecutable<EcsExecCommandExecutable>().thenAccept { ecsExecExecutable ->
            when (ecsExecExecutable) {
                is ExecutableInstance.Executable -> ecsExecExecutable
                is ExecutableInstance.UnresolvedExecutable -> throw Exception("Couldn't resolve executable")
                is ExecutableInstance.InvalidExecutable -> throw Exception(ecsExecExecutable.validationError)
            }

            val cmdLine = buildBaseCmdLine(project, ecsExecExecutable)
                .withParameters("ecs")
                .withParameters("execute-command")
                .withParameters("--cluster")
                .withParameters(container.service.clusterArn())
                .withParameters("--task")
                .withParameters(task)
                .withParameters("--command")
                .withParameters(commandToExecute)
                .withParameters("--interactive")

            val cmdList = cmdLine.getCommandLineList(null).toTypedArray()
            val env = cmdLine.effectiveEnvironment
            val ptyProcess = PtyProcess.exec(cmdList, env, null)
        }
    }
    private fun buildBaseCmdLine(project: Project, executable: ExecutableInstance.Executable) = executable.getCommandLine()
        .withEnvironment(project.activeRegion().toEnvironmentVariables())
        .withEnvironment(project.activeCredentialProvider().resolveCredentials().toEnvironmentVariables())
}
