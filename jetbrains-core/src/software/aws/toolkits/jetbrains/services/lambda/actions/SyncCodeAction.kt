// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.core.experiments.isEnabled
import software.aws.toolkits.jetbrains.services.lambda.SyncCodeWarningDialog
import software.aws.toolkits.jetbrains.services.lambda.SyncServerlessApplicationCodeExperiment
import software.aws.toolkits.jetbrains.services.lambda.SyncServerlessApplicationExperiment
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import software.aws.toolkits.jetbrains.services.lambda.wizard.SyncServerlessApplicationDialog2
import software.aws.toolkits.jetbrains.settings.SamDisplayDevModeWarningSettings
import java.nio.charset.Charset
import java.nio.file.Path
import javax.swing.Icon

class SyncCodeAction : AnAction("Sync Serverless Application Code(Skip infra)", "", AwsIcons.Resources.SERVERLESS_APP) {
    private val templateYamlRegex = Regex("template\\.y[a]?ml", RegexOption.IGNORE_CASE)
    override fun actionPerformed(e: AnActionEvent) {
        try {
            val project = e.project
            if (project != null) {
                val warningSettings = SamDisplayDevModeWarningSettings.getInstance()
                val devMode = if (warningSettings.showDevModeWarning) SyncCodeWarningDialog(project).showAndGet() else true
                if (devMode) {
                    val templateFile = getSamTemplateFile(e)
                    if (templateFile != null) {
                        // DeployServerlessApplicationDialog(project, templateFile).show()
                        SyncServerlessApplicationDialog2(project, templateFile).show()
                    }

                    val connectionSettings = project.getConnectionSettingsOrThrow()
                    val templatePath = getSamTemplateFile(e)?.toNioPath() ?: throw Exception("Empty")
                    val environment = ExecutionEnvironmentBuilder.create(
                        project,
                        DefaultRunExecutor.getRunExecutorInstance(),
                        SyncCodeRunProfile(project, "sam-app", project.getConnectionSettingsOrThrow(), templatePath, true)
                    ).build()

                    runInEdt {
                        environment.runner.execute(environment)
                    }
                    /*val a = getClis2().apply {
                        withEnvironment(connectionSettings.toEnvironmentVariables())
                        withWorkDirectory(templatePath.toAbsolutePath().parent.toString())
                        addParameter("sync")
                        addParameter("--stack-name")
                        addParameter("sam-app")
                        addParameter("--code")
                    }
                    val ptyProcess = PtyCommandLine(a).createProcess()
                    val process = CloudTerminalProcess(ptyProcess.outputStream, ptyProcess.inputStream)
                    val runner = CloudTerminalRunner(project, "sam-11", process)

                    runInEdt(ModalityState.any()) {
                        TerminalView.getInstance(project).createNewSession(runner, TerminalTabState().also { it.myTabName = "sam-11" })
                    }*/
                }
            }
        } catch (e: Exception) {
            println(e)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = getSamTemplateFile(e) != null && SyncServerlessApplicationCodeExperiment.isEnabled() && SyncServerlessApplicationExperiment.isEnabled()
    }
    private fun getSamTemplateFile(e: AnActionEvent): VirtualFile? = runReadAction {
        val virtualFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY) ?: return@runReadAction null
        val virtualFile = virtualFiles.singleOrNull() ?: return@runReadAction null

        if (templateYamlRegex.matches(virtualFile.name)) {
            return@runReadAction virtualFile
        }

        // If the module node was selected, see if there is a template file in the top level folder
        val module = e.getData(LangDataKeys.MODULE_CONTEXT)
        if (module != null) {
            // It is only acceptable if one template file is found
            val childTemplateFiles = ModuleRootManager.getInstance(module).contentRoots.flatMap { root ->
                root.children.filter { child -> templateYamlRegex.matches(child.name) }
            }

            if (childTemplateFiles.size == 1) {
                return@runReadAction childTemplateFiles.single()
            }
        }

        return@runReadAction null
    }
    private fun getClis2(): GeneralCommandLine {
        val executable = runBlocking {
            ExecutableManager.getInstance().getExecutable<SamExecutable>().await()
        }
        val samExecutable = when (executable) {
            is ExecutableInstance.Executable -> executable
            else -> {
                throw RuntimeException((executable as? ExecutableInstance.BadExecutable)?.validationError ?: "")
            }
        }

        return samExecutable.getCommandLine()
    }
}

class SyncCodeRunProfile(
    private val project: Project,
    private val stackName: String,
    private val connection: ConnectionSettings,
    private val templatePath: Path,
    private val syncOnlyCode: Boolean
) : RunProfile {
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = SyncCodeRunProfileState(environment)

    override fun getName(): String = stackName

    override fun getIcon(): Icon? = null

    inner class SyncCodeRunProfileState(environment: ExecutionEnvironment) : CommandLineState(environment) {
        override fun startProcess(): ProcessHandler {
            val a = if (syncOnlyCode) {
                getClis().apply {
                    withEnvironment(connection.toEnvironmentVariables())
                    withWorkDirectory(templatePath.toAbsolutePath().parent.toString())
                    addParameter("sync")
                    addParameter("--stack-name")
                    addParameter(stackName)
                    addParameter("--code")
                }
            } else {
                getClis().apply {
                    withEnvironment(connection.toEnvironmentVariables())
                    withWorkDirectory(templatePath.toAbsolutePath().parent.toString())
                    addParameter("sync")
                    addParameter("--stack-name")
                    addParameter(stackName)
                }
            }

            val processHandler = KillableProcessHandler(a)

            ProcessTerminatedListener.attach(processHandler)
            return processHandler
        }

        override fun execute(executor: Executor, runner: ProgramRunner<*>) = super.execute(executor, runner).apply {
            processHandler?.addProcessListener(object : ProcessAdapter() {
                override fun startNotified(event: ProcessEvent) {
                    super.startNotified(event)
                    ApplicationManager.getApplication().executeOnPooledThread {
                        /*while(true){
                        processHandler.processInput?.write("Y\n".toByteArray(Charset.defaultCharset()))
                        }*/
                    }
                }

                override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                    super.processWillTerminate(event, willBeDestroyed)
                }

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType === ProcessOutputTypes.STDOUT ||
                        outputType === ProcessOutputTypes.STDERR
                    ) {
                        println("text ${event.text}")
                        if (event.text.contains("[Y/n]:")) {
                            cnt = true
                            println("Broooooooooo")

                            ApplicationManager.getApplication().executeOnPooledThread {
                                // var cnt =0
                                while (cnt != false) {
                                    processHandler.processInput?.write("Y\n".toByteArray(Charset.defaultCharset()))
                                    println("let's see this")

                                    // cnt+=1
                                }

                                // processHandler.processInput?.write("Y\n".toByteArray(Charset.defaultCharset()))
                            }
                        } else {
                            cnt = false
                        }
                        println("cnt $cnt")
                        // RunContentManager.getInstance(project).
                        runInEdt {
                            // RunContentManager.getInstance(project).
                            RunContentManager.getInstance(project).toFrontRunContent(executor, processHandler)
                        }
                    }
                }
            })
        }

        private fun getClis(): GeneralCommandLine {
            val executable = runBlocking {
                ExecutableManager.getInstance().getExecutable<SamExecutable>().await()
            }
            val samExecutable = when (executable) {
                is ExecutableInstance.Executable -> executable
                else -> {
                    throw RuntimeException((executable as? ExecutableInstance.BadExecutable)?.validationError ?: "")
                }
            }

            return samExecutable.getCommandLine()
        }
    }

    companion object {
        var cnt = false
    }
}
