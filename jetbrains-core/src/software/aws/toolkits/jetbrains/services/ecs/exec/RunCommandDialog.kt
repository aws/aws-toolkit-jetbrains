// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.PtyBasedProcess
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.Content
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import com.pty4j.PtyProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import software.aws.toolkits.jetbrains.core.getResource
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import java.util.concurrent.TimeUnit
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

class RunCommandDialog(private val project: Project, private val container: ContainerDetails) : DialogWrapper(project) {
    private val resourceCache = AwsResourceCache.getInstance()
    private val tasks = resourceCache.getResourceNow(EcsResources.listTasks(container.service.clusterArn(),container.service.serviceArn()),project.activeRegion(),project.activeCredentialProvider())
    private val taskList = DefaultComboBoxModel(tasks.toTypedArray())
    private val command = JBTextField()
    private val component by lazy {
        panel{
            row("Task"){
                comboBox(taskList,{tasks.first()},{tasks.first()}).constraints(growX)
            }
            row("Command)"){
                command(grow)
            }
        }
    }
    init {
        super.init()
        title = "Run Command in Container"
        setOKButtonText("Execute")
    }
    override fun createCenterPanel(): JComponent? = component

    override fun doOKAction() {
        super.doOKAction()
        constructExecCommand(command.text)
    }
    private fun constructExecCommand(commandToExecute: String) {

        ExecutableManager.getInstance().getExecutable<EcsExecCommandExecutable>().thenAccept{ ecsExecExecutable->
            if(ecsExecExecutable !is ExecutableInstance.Executable){
                throw Exception ("Not found stuff")
            }

            val cmdLine = buildBaseCmdLine(project, ecsExecExecutable)
                .withParameters("ecs")
                .withParameters("execute-command")
                .withParameters("--cluster")
                .withParameters(container.service.clusterArn())
                .withParameters("--task")
                .withParameters(taskList.selectedItem.toString())
                .withParameters("--command")
                .withParameters(commandToExecute)
                .withParameters("--interactive")
            val cmdList = cmdLine.getCommandLineList(null).toTypedArray()
            val env = cmdLine.effectiveEnvironment
            val ptyProcess = PtyProcess.exec(cmdList, env, null)

            val process = CloudTerminalProcess(ptyProcess.outputStream, ptyProcess.inputStream)
            val runner = CloudTerminalRunner(project, container.containerDefinition.name(), process)

            runInEdt {
                //val termv = TerminalView.getInstance(project).openTerminalIn()
                TerminalView.getInstance(project).createNewSession(runner, TerminalTabState().also { it.myTabName = container.containerDefinition.name() })
            }
        }
    }
    private fun buildBaseCmdLine(project: Project, executable: ExecutableInstance.Executable) = executable.getCommandLine()
        .withEnvironment(project.activeRegion().toEnvironmentVariables())
        .withEnvironment(project.activeCredentialProvider().resolveCredentials().toEnvironmentVariables())
}
