// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.panel
import com.pty4j.PtyProcess
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalView
import org.jetbrains.plugins.terminal.cloud.CloudTerminalProcess
import org.jetbrains.plugins.terminal.cloud.CloudTerminalRunner
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

class OpenShellInContainerDialog(private val project: Project, private val container: ContainerDetails) : DialogWrapper(project) {
    private val resourceCache = AwsResourceCache.getInstance()
    private val tasks = resourceCache.getResourceNow(
        EcsResources.listTasks(
            container.service.clusterArn(),
            container.service.serviceArn()
        ),
        project.activeRegion(),
        project.activeCredentialProvider(),
    )
    private val shellList = listOf("bash", "sh", "zsh")
    private val shellOption = DefaultComboBoxModel(shellList.toTypedArray())
    private val taskList = DefaultComboBoxModel(tasks.toTypedArray())
    private val shell = ComboBox(shellOption)
    var task = if (tasks.isNotEmpty()) tasks.first() else null
    private val component by lazy {
        panel {
            row(message("ecs.execute_command_task")) {
                comboBox(taskList, { task }, { if (it != null) { task = it } })
                    .constraints(growX)
                    .withErrorOnApplyIf(message("ecs.execute_command_task_comboBox_empty")) { it.selected() == null }
            }
            row(message("ecs.execute_command_shell")) {
                shell()
                    .constraints(growX)
                    .withErrorOnApplyIf(message("ecs.execute_command_shell_comboBox_empty")) { it.selected() == null || it.selected() == "" }
            }
        }
    }
    init {
        super.init()
        title = message("ecs.execute_command_run_command_in_shell")
        setOKButtonText(message("general.execute_button"))
        shell.isEditable = true
    }
    override fun createCenterPanel(): JComponent? = component

    override fun doOKAction() {
        super.doOKAction()
        runExecCommand()
    }

    private fun runExecCommand() {
        ExecutableManager.getInstance().getExecutable<AwsCliExecutable>().thenAccept { awsCliExecutable ->
            when (awsCliExecutable) {
                is ExecutableInstance.Executable -> awsCliExecutable
                is ExecutableInstance.UnresolvedExecutable -> throw Exception("Couldn't resolve executable")
                is ExecutableInstance.InvalidExecutable -> throw Exception(awsCliExecutable.validationError)
            }

            val cmdLine = buildBaseCmdLine(project, awsCliExecutable)
                .withParameters("ecs")
                .withParameters("execute-command")
                .withParameters("--cluster")
                .withParameters(container.service.clusterArn())
                .withParameters("--task")
                .withParameters(task)
                .withParameters("--command")
                .withParameters("/bin/" + shell.item)
                .withParameters("--interactive")

            val cmdList = cmdLine.getCommandLineList(null).toTypedArray()
            val env = cmdLine.effectiveEnvironment
            val ptyProcess = PtyProcess.exec(cmdList, env, null)
            val process = CloudTerminalProcess(ptyProcess.outputStream, ptyProcess.inputStream)
            val runner = CloudTerminalRunner(project, container.containerDefinition.name(), process)

            runInEdt {
                TerminalView.getInstance(project).createNewSession(runner, TerminalTabState().also { it.myTabName = container.containerDefinition.name() })
            }
        }
    }
    private fun buildBaseCmdLine(project: Project, executable: ExecutableInstance.Executable) = executable.getCommandLine()
        .withEnvironment(project.activeRegion().toEnvironmentVariables())
        .withEnvironment(project.activeCredentialProvider().resolveCredentials().toEnvironmentVariables())
}
