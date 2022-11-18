// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.SemVer
import icons.AwsIcons
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.services.lambda.SyncServerlessAppWarningDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateFileUtils.retrieveSamTemplate
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateFileUtils.validateTemplateFile
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncApplicationRunProfile
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncServerlessApplicationDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncServerlessApplicationSettings
import software.aws.toolkits.jetbrains.settings.SamDisplayDevModeWarningSettings
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyNoActiveCredentialsError
import software.aws.toolkits.jetbrains.utils.notifySamCliNotValidError
import software.aws.toolkits.resources.message

class SyncServerlessAppAction(private val codeOnly: Boolean = false) : AnAction(
    message("serverless.application.sync"),
    null,
    AwsIcons.Resources.SERVERLESS_APP
) {
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

            val execVersion = SemVer.parseFromText(samExecutable.version) ?: error(" SAM CLI version could not detected")
            val minVersion = SemVer("1.53.0", 1, 53, 0)
            val minVersionForUseContainer = SemVer("1.57.0", 1, 57, 0)
            if (!execVersion.isGreaterOrEqualThan(minVersion)) {
                notifyError(message("sam.cli.version.warning"), message("sam.cli.version.upgrade.required"), project = project)
                return@thenAccept
            }

            val templateFile = retrieveSamTemplate(e, project) ?: return@thenAccept

            validateTemplateFile(project, templateFile)?.let {
                notifyError(content = it, project = project)
                return@thenAccept
            }

            val warningSettings = SamDisplayDevModeWarningSettings.getInstance()
            runInEdt {
                if (warningSettings.showDevModeWarning) {
                    if (!SyncServerlessAppWarningDialog(project).showAndGet()) {
                        return@runInEdt
                    }
                }

                FileDocumentManager.getInstance().saveAllDocuments()
                val parameterDialog = SyncServerlessApplicationDialog(project, templateFile)
                if (!parameterDialog.showAndGet()) {
                    // add telemetry
                    return@runInEdt
                }

                val settings = parameterDialog.settings()

                // TODO: saveSettings(project, templateFile, settings)

                if (settings.useContainer) {
                    if (!execVersion.isGreaterOrEqualThan(minVersionForUseContainer)) {
                        notifyError(message("sam.cli.version.warning"), message("sam.cli.version.upgrade.required"), project = project)
                        return@runInEdt
                    }
                    val dockerDoesntExist = runBlocking(getCoroutineBgContext()) {
                        try {
                            val processOutput = ExecUtil.execAndGetOutput(GeneralCommandLine("docker", "ps"))
                            processOutput.exitCode != 0
                        } catch (e: Exception) {
                            true
                        }
                    }
                    if (dockerDoesntExist) {
                        notifyError(message("docker.not.found"), message("lambda.debug.docker.not_connected"))
                        return@runInEdt
                    }
                }
                syncApp(templateFile, project, settings)
            }
        }
    }

    private fun syncApp(
        templateFile: VirtualFile,
        project: Project,
        settings: SyncServerlessApplicationSettings
    ) {
        val templatePath = templateFile.toNioPath()
        val environment = ExecutionEnvironmentBuilder.create(
            project,
            DefaultRunExecutor.getRunExecutorInstance(),
            SyncApplicationRunProfile(project, settings, project.getConnectionSettingsOrThrow(), templatePath, codeOnly)
        ).build()

        environment.runner.execute(environment)
    }
}
