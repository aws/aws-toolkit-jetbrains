// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons
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

                syncApp(templateFile, project, settings)
            }
        }
    }

    private fun syncApp(
        templateFile: VirtualFile,
        project: Project,
        settings: SyncServerlessApplicationSettings
    ) {
        if (settings.useContainer) {
            // TODO: Check if docker exists
        }
        val templatePath = templateFile.toNioPath()
        val environment = ExecutionEnvironmentBuilder.create(
            project,
            DefaultRunExecutor.getRunExecutorInstance(),
            SyncApplicationRunProfile(project, settings, project.getConnectionSettingsOrThrow(), templatePath, codeOnly)
        ).build()

        environment.runner.execute(environment)
    }
}
