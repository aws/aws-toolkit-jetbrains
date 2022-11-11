// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons
import kotlinx.coroutines.runBlocking
import org.jetbrains.yaml.YAMLFileType
import software.aws.toolkits.jetbrains.ToolkitPlaces
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineUiContext
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.services.cloudformation.validateSamTemplateHasResources
import software.aws.toolkits.jetbrains.services.lambda.SyncCodeWarningDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncApplicationRunProfile
import software.aws.toolkits.jetbrains.services.lambda.wizard.SyncServerlessApplicationDialog2
import software.aws.toolkits.jetbrains.services.lambda.wizard.SyncServerlessApplicationSettings
import software.aws.toolkits.jetbrains.settings.SamDisplayDevModeWarningSettings
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyNoActiveCredentialsError
import software.aws.toolkits.jetbrains.utils.notifySamCliNotValidError
import software.aws.toolkits.resources.message

class SyncServerlessAppAction(private val codeOnly: Boolean = false) : AnAction("Sync Serverless Application", null, AwsIcons.Resources.SERVERLESS_APP) {
    private val edtContext = getCoroutineUiContext()
    private val templateYamlRegex = Regex("template\\.y[a]?ml", RegexOption.IGNORE_CASE)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(PlatformDataKeys.PROJECT)

        if (!AwsConnectionManager.getInstance(project).isValidConnectionSettings()) {
            notifyNoActiveCredentialsError(project = project)
            return
        }

        ExecutableManager.getInstance().getExecutable<SamExecutable>().thenAccept { samExecutable ->
            if (samExecutable is ExecutableInstance.InvalidExecutable || samExecutable is ExecutableInstance.UnresolvedExecutable) {
                notifySamCliNotValidError(
                    project = project,
                    content = (samExecutable as ExecutableInstance.BadExecutable).validationError
                )
                return@thenAccept
            }

            val templateFile = if (e.place == ToolkitPlaces.EXPLORER_TOOL_WINDOW) {
                runBlocking(edtContext) {
                    FileChooser.chooseFile(
                        FileChooserDescriptorFactory.createSingleFileDescriptor(YAMLFileType.YML),
                        project,
                        project.guessProjectDir()
                    )
                } ?: return@thenAccept
            } else {
                val file = getSamTemplateFile(e)
                if (file == null) {
                    Exception(message("serverless.application.deploy.toast.template_file_failure"))
                        .notifyError(message("aws.notification.title"), project)
                    return@thenAccept
                }
                file
            }

            validateTemplateFile(project, templateFile)?.let {
                notifyError(content = it, project = project)
                return@thenAccept
            }

            val warningSettings = SamDisplayDevModeWarningSettings.getInstance()
            val devMode = if (warningSettings.showDevModeWarning) SyncCodeWarningDialog(project).showAndGet() else true
            if (devMode) {
                runInEdt {
                    val parameterDialog = SyncServerlessApplicationDialog2(project, templateFile)
                    if (!parameterDialog.showAndGet()) {
                        // add telemetry
                        return@runInEdt
                    }
                    val settings = parameterDialog.settings()

                    // TODO: saveSettings(project, templateFile, settings)

                    syncApp(templateFile, project, settings)
                }
            }
        }
    }

    private fun syncApp(
        templateFile: VirtualFile,
        project: Project,
        settings: SyncServerlessApplicationSettings
    ) {
        if (settings.useContainer) {
            val doesDockerExist = ExecUtil.execAndGetOutput(GeneralCommandLine("docker", "ps"))
            // TODO: Solve this
            throw Exception(message("lambda.debug.docker.not_connected"))
        }
        val templatePath = templateFile.toNioPath()
        val environment = ExecutionEnvironmentBuilder.create(
            project,
            DefaultRunExecutor.getRunExecutorInstance(),
            SyncApplicationRunProfile(project, settings, project.getConnectionSettingsOrThrow(), templatePath, codeOnly)
        ).build()

        environment.runner.execute(environment)
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

    private fun validateTemplateFile(project: Project, templateFile: VirtualFile): String? =
        try {
            project.validateSamTemplateHasResources(templateFile)
        } catch (e: Exception) {
            message("serverless.application.deploy.error.bad_parse", templateFile.path, e)
        }
}
