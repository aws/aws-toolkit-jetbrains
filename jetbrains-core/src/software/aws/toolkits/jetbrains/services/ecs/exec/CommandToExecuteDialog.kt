// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.enableIf
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.jetbrains.rd.util.string.printToString
import com.pty4j.PtyProcess
import kotlinx.coroutines.Dispatchers
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
import software.aws.toolkits.jetbrains.core.executables.ExecutableCommon
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.core.getResource
import software.aws.toolkits.jetbrains.core.map
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.resources.CloudWatchResources
import software.aws.toolkits.jetbrains.services.ecs.ContainerDetails
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources
import software.aws.toolkits.jetbrains.services.s3.resources.S3Resources
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent

class CommandToExecuteDialog(
    private val project: Project,
    private val container: ContainerDetails
) : DialogWrapper(project)  {
    private val commandToExecute = JBTextField()
    private val a = JBTextField()
    private val b = JBTextField()
    private val kmsKeyName = JBTextField()
    private val generateLogs = JBCheckBox()
    private val createS3BucketButton = JButton().also{it.text = "Create Bucket"}
    private val createLogGroupButton = JButton().also{it.text = "Create Log Group"}
    private val generateKmsKeyButton = JButton().also{it.text = "Generate Key"}
    private val resourceCache = AwsResourceCache.getInstance()
    private val s3BucketName = resourceCache.getResourceNow(S3Resources.LIST_BUCKETS,project.activeRegion(),project.activeCredentialProvider()).map { it.name() }
    private val cloudWatchLogGroupName = resourceCache.getResourceNow(CloudWatchResources.LIST_LOG_GROUPS,project.activeRegion(),project.activeCredentialProvider()).map { it.logGroupName() }
    private val containers = resourceCache.getResourceNow(EcsResources.listContainers(container.service.taskDefinition()),project.activeRegion(),project.activeCredentialProvider()).map {it.name()}
    private val containerName = DefaultComboBoxModel(containers.toTypedArray())

    private val component by lazy {
        panel {
           /* row("Task"){
                //comboBox(containerName,{containers.first()}, {containers.first()}).constraints(growX)
                comboBox(containerName,{null}, {null}).constraints(growX)
            }*/
            /*row("Container"){
                //comboBox(containerName,{containers.first()}, {containers.first()}).constraints(growX)
                comboBox(containerName,{null}, {null}).constraints(growX)
            }*/
            row{
                label("example-task-id")
            }
            row("Command") {
                commandToExecute(grow)
            }
            /*row ("Generate Logs"){
                generateLogs(grow)
            }
            row("S3 Bucket"){
                //comboBox(DefaultComboBoxModel(s3BucketName.toTypedArray()),{s3BucketName.first()},{s3BucketName.first()})
                a(CCFlags.pushX)
                createS3BucketButton(grow)
            }.enableIf(generateLogs.selected)
            row ("Log Group"){
                //comboBox(DefaultComboBoxModel(cloudWatchLogGroupName.toTypedArray()),{cloudWatchLogGroupName.first()},{cloudWatchLogGroupName.first()})
                b(pushX)
                createLogGroupButton(grow)
            }.enableIf(generateLogs.selected)
            row ("KMS Key"){
                kmsKeyName(pushX)
                generateKmsKeyButton(grow)
            }.enableIf(generateLogs.selected)*/
        }
    }

    init{
        super.init()
        title = "Run Command in Container"
        setOKButtonText("Execute")
    }


    override fun doOKAction() {
        super.doOKAction()

        //project.getResource(EcsResources.listTaskIds(container.service.clusterArn(), container.service.serviceArn()))
       constructExecCommand(commandToExecute.text)
    }

    fun constructExecCommand(userCommandToExecute: String) {

      /*ExecutableManager.getInstance().getExecutable<EcsExecCommandExecutable>().thenAccept{ecsExecExecutable->
          if(ecsExecExecutable !is ExecutableInstance.Executable){
              throw Exception ("Not found stuff")
          }
          val cmdLine = buildBaseCmdLine( project, ecsExecExecutable)
              .withParameters("session-manager-plugin")
          runBlocking(Dispatchers.IO) {
              val process = CapturingProcessHandler(cmdLine).runProcess().exitCode
              println(process)
          }




          val cmdLine = buildBaseCmdLine( project, ecsExecExecutable)
              .withParameters("exec")
              .withParameters("--cluster")
              .withParameters("default2")
              .withParameters("--task")
              .withParameters("4c43347c4ec0447b93af9a6510971546")
              .withParameters("--command")
              .withParameters(userCommandToExecute)
              .withParameters("--interactive")
          val cmdList = cmdLine.getCommandLineList(null).toTypedArray()
          val env = cmdLine.effectiveEnvironment
          val ptyProcess = PtyProcess.exec(cmdList, env, null)
          //val a = KillableColoredProcessHandler(cmdLine).process.outputStream
          //val b = KillableColoredProcessHandler(cmdLine).process.inputStream
          //val process = CloudTerminalProcess(a, b)
          val process = CloudTerminalProcess(ptyProcess.outputStream, ptyProcess.inputStream)
          val runner = CloudTerminalRunner(project, container.containerDefinition.name(), process)

          runInEdt {
              TerminalView.getInstance(project).createNewSession(runner, TerminalTabState().also { it.myTabName = container.containerDefinition.name() })

          }
           //val op =   CapturingProcessHandler(cmdLine).runProcess().stdout


      }*/
    }

    private fun buildBaseCmdLine(project: Project, executable: ExecutableInstance.Executable) = executable.getCommandLine()
        .withEnvironment(project.activeRegion().toEnvironmentVariables())
        .withEnvironment(project.activeCredentialProvider().resolveCredentials().toEnvironmentVariables())

    override fun createCenterPanel() : JComponent = component
}
