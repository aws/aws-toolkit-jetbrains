// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.services.lambda.SyncCodeWarningDialog
import software.aws.toolkits.jetbrains.services.lambda.SyncServerlessApplicationDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import software.aws.toolkits.jetbrains.settings.SamDisplayDevModeWarningSettings

// For main option it will be Sync Serverless Application
class SyncInfraAction: AnAction({"Sync Serverless Application"}, AwsIcons.Resources.SERVERLESS_APP) {
    private val templateYamlRegex = Regex("template\\.y[a]?ml", RegexOption.IGNORE_CASE)
    override fun actionPerformed(e: AnActionEvent) {
        try {
            val project = e.project
            if(project != null){
                val warningSettings = SamDisplayDevModeWarningSettings.getInstance()
                val devMode = if(warningSettings.showDevModeWarning) SyncCodeWarningDialog(project).showAndGet() else true
                if(devMode) {
                    val templateFile = getSamTemplateFile(e)
                    if (templateFile != null) {
                        SyncServerlessApplicationDialog(project, templateFile).show()
                    }
                    val connectionSettings = project.getConnectionSettingsOrThrow()
                    val templatePath = getSamTemplateFile(e)?.toNioPath() ?: throw Exception("Empty")
                    val environment = ExecutionEnvironmentBuilder.create(
                        project,
                        DefaultRunExecutor.getRunExecutorInstance(),
                        SyncCodeRunProfile(project, "sam-app", project.getConnectionSettingsOrThrow(), templatePath, false)
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
