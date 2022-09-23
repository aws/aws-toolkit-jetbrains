// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
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
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.pty4j.PtyProcess
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.gradle.api.reporting.Report.OutputType
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalView
import org.jetbrains.plugins.terminal.cloud.CloudTerminalProcess
import org.jetbrains.plugins.terminal.cloud.CloudTerminalRunner
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.toEnvironmentVariables
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.services.ecs.exec.EcsExecUtils
import software.aws.toolkits.jetbrains.services.ecs.exec.RunCommandRunProfile
import software.aws.toolkits.jetbrains.services.lambda.SyncCodeWarningDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import java.nio.charset.Charset
import java.nio.file.Path
import javax.swing.Icon

class SyncCodeAction: AnAction("SAM Sync Code") {
    private val templateYamlRegex = Regex("template\\.y[a]?ml", RegexOption.IGNORE_CASE)
    override fun actionPerformed(e: AnActionEvent) {
        try {
            val project = e.project
            if(project != null){
                val devMode = SyncCodeWarningDialog(project).showAndGet()
                if(devMode) {
                    val connectionSettings = project.getConnectionSettingsOrThrow()
                    val templatePath = getSamTemplateFile(e)?.toNioPath() ?: throw Exception("Empty")
                    val environment = ExecutionEnvironmentBuilder.create(
                        project,
                        DefaultRunExecutor.getRunExecutorInstance(),
                        SyncCodeRunProfile(project, "sam-app", project.getConnectionSettingsOrThrow(), templatePath)
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

        }catch (e: Exception){
            println(e)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = getSamTemplateFile(e) != null
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
    private val templatePath: Path
) : RunProfile {
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState= SyncCodeRunProfileState(environment)

    override fun getName(): String = stackName

    override fun getIcon(): Icon? = null

    inner class SyncCodeRunProfileState(environment: ExecutionEnvironment) : CommandLineState(environment) {
        override fun startProcess(): ProcessHandler {
            val a = getClis().apply {
                withEnvironment(connection.toEnvironmentVariables())
                withWorkDirectory(templatePath.toAbsolutePath().parent.toString())
                addParameter("sync")
                addParameter("--stack-name")
                addParameter(stackName)
                addParameter("--code")
            }


            val processHandler = ColoredProcessHandler(a)

            ProcessTerminatedListener.attach(processHandler)
            return processHandler
        }

        override fun execute(executor: Executor, runner: ProgramRunner<*>) = super.execute(executor, runner).apply {
            processHandler?.addProcessListener(object : ProcessAdapter() {


                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType === ProcessOutputTypes.STDOUT ||
                        outputType === ProcessOutputTypes.STDERR
                    ) {
                        if(event.text == "[Y/n]:"){
                            //processHandler.processInput?.write("Y".toByteArray(Charset.defaultCharset()))
                            println("okayyyyyyy")
                        }
                        //RunContentManager.getInstance(project).
                        runInEdt {
                            //RunContentManager.getInstance(project).
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

}
