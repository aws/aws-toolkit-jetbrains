// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.clouddebug.actions

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import org.slf4j.event.Level
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.clearResourceForCurrentConnection
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.core.explorer.ExplorerToolWindow
import software.aws.toolkits.jetbrains.services.clouddebug.CliOutputParser
import software.aws.toolkits.jetbrains.services.clouddebug.CloudDebugExecutable
import software.aws.toolkits.jetbrains.services.clouddebug.CloudDebugResolver
import software.aws.toolkits.jetbrains.services.clouddebug.asLogEvent
import software.aws.toolkits.jetbrains.services.clouddebug.execution.DefaultMessageEmitter
import software.aws.toolkits.jetbrains.services.clouddebug.resources.CloudDebuggingResources
import software.aws.toolkits.jetbrains.services.ecs.EcsClusterNode
import software.aws.toolkits.jetbrains.services.ecs.EcsServiceNode
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources.describeService
import software.aws.toolkits.jetbrains.services.ecs.resources.EcsResources.listServiceArns
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

// TODO refactor this whole file
abstract class PseCliAction(val project: Project, val actionName: String, private val successMessage: String, private val failureMessage: String) {
    abstract fun buildCommandLine(cmd: GeneralCommandLine)
    protected abstract fun produceTelemetry(startTime: Instant, result: Result, version: String?)

    fun runAction(selectedNode: AbstractTreeNode<*>? = null, callback: ((Boolean) -> Unit)? = null) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                actionName,
                false,
                PerformInBackgroundOption.ALWAYS_BACKGROUND
            ) {
                override fun run(indicator: ProgressIndicator) {

                    val startTime = Instant.now()
                    val buildViewManager = ServiceManager.getService(project, BuildViewManager::class.java)
                    val descriptor = DefaultBuildDescriptor(
                        actionName,
                        actionName,
                        "",
                        System.currentTimeMillis()
                    )
                    val messageEmitter = DefaultMessageEmitter.createRoot(buildViewManager, actionName)
                    buildViewManager.onEvent(actionName, StartBuildEventImpl(descriptor, ""))

                    val toolWindowManager = ToolWindowManager.getInstance(project)

                    runInEdt {
                        // Safe access because it is possible to close the window before this completes
                        toolWindowManager.getToolWindow(ToolWindowId.BUILD)?.show(null)
                    }
                    // validate CLI
                    CloudDebugResolver.validateOrUpdateCloudDebug(project, messageEmitter, null)

                    val region = AwsConnectionManager.getInstance(project).activeRegion.toEnvironmentVariables()
                    val credentials = AwsConnectionManager.getInstance(project).activeCredentialProvider.resolveCredentials().toEnvironmentVariables()

                    val clouddebug = ExecutableManager.getInstance().getExecutable<CloudDebugExecutable>().thenApply {
                        if (it is ExecutableInstance.Executable) {
                            it
                        } else {
                            val error = (it as? ExecutableInstance.BadExecutable)?.validationError ?: message("general.unknown_error")
                            val errorMessage = message("cloud_debug.step.clouddebug.install.fail", error)
                            val exception = Exception(errorMessage)
                            LOG.error(exception) { "Setting cloud-debug executable failed" }
                            notifyError(message("aws.notification.title"), errorMessage, project)
                            produceTelemetry(startTime, Result.Failed, null)
                            messageEmitter.finishExceptionally(exception)
                            null
                        }
                    }.toCompletableFuture().join() ?: run {
                        callback?.invoke(false)
                        return
                    }

                    val cmd = clouddebug.getCommandLine()

                    cmd
                        .withEnvironment(region)
                        .withEnvironment(credentials)

                    buildCommandLine(cmd)

                    val handler = CapturingProcessHandler(cmd)

                    handler.addProcessListener(
                        object : ProcessAdapter() {
                            val cliOutput = AtomicReference<String?>()
                            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                                if (outputType == ProcessOutputTypes.STDOUT) {
                                    cliOutput.set(event.text)
                                } else {
                                    val (text, level) = event.text.asLogEvent()
                                    @Suppress("DEPRECATION")
                                    messageEmitter.emitMessage(text, level == Level.ERROR)
                                    indicator.text2 = text
                                    // output to the log for diagnostic and integrations tests
                                    LOG.debug { event.text.trim() }
                                }
                            }

                            override fun processTerminated(event: ProcessEvent) {
                                val result = if (event.exitCode == 0) {
                                    SuccessResultImpl()
                                } else {
                                    // TODO: really need to refactor this and steps - it's getting a bit crazy
                                    messageEmitter.emitMessage("Error details:\n", true)
                                    cliOutput.get()?.let { CliOutputParser.parseErrorOutput(it) }?.errors?.forEach {
                                        messageEmitter.emitMessage("\t- $it\n", true)
                                        // output to the log for diagnostic and integrations tests
                                        LOG.debug { "Error details:\n $it" }
                                    }
                                    FailureResultImpl()
                                }
                                buildViewManager.onEvent(actionName, FinishBuildEventImpl(actionName, null, System.currentTimeMillis(), "", result))
                            }
                        }
                    )

                    val exit = handler.runProcess().exitCode
                    if (exit == 0) {
                        notifyInfo(
                            actionName,
                            successMessage,
                            project
                        )
                        // reset the cache
                        project.clearResourceForCurrentConnection(CloudDebuggingResources.LIST_INSTRUMENTED_RESOURCES)
                        callback?.invoke(true)
                    } else {
                        notifyError(
                            actionName,
                            failureMessage,
                            project
                        )
                        callback?.invoke(false)
                    }

                    // Redraw cluster level if the action was taken from a node
                    if (selectedNode is EcsServiceNode) {
                        val parent = selectedNode.parent
                        if (parent is EcsClusterNode) {
                            // dump cached values relating to altered service
                            project.clearResourceForCurrentConnection(describeService(parent.resourceArn(), selectedNode.resourceArn()))
                            project.clearResourceForCurrentConnection(listServiceArns(parent.resourceArn()))
                            runInEdt {
                                // redraw explorer from the cluster downwards
                                val explorer = ExplorerToolWindow.getInstance(project)
                                explorer.invalidateTree(parent)
                            }
                        }
                        // If this wasn't run through a node, just redraw the whole tree
                        // Open to suggestions to making this smarter.
                    } else {
                        project.clearResourceForCurrentConnection()
                        runInEdt {
                            // redraw explorer from the cluster downwards
                            val explorer = ExplorerToolWindow.getInstance(project)
                            explorer.invalidateTree()
                        }
                    }

                    val result = if (exit == 0) Result.Succeeded else Result.Failed
                    produceTelemetry(startTime, result, clouddebug.version)
                }
            }
        )
    }

    companion object {
        val LOG = getLogger<PseCliAction>()
    }
}
