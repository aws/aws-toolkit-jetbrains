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
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.AwsIcons
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.lambda.model.PackageType
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.getConnectionSettingsOrThrow
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.services.cloudformation.SamFunction
import software.aws.toolkits.jetbrains.services.lambda.SyncServerlessAppWarningDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.SamCommon
import software.aws.toolkits.jetbrains.services.lambda.sam.SamExecutable
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateFileUtils.retrieveSamTemplate
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateFileUtils.validateTemplateFile
import software.aws.toolkits.jetbrains.services.lambda.sam.SamTemplateUtils
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncApplicationRunProfile
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncServerlessApplicationDialog
import software.aws.toolkits.jetbrains.services.lambda.sam.sync.SyncServerlessApplicationSettings
import software.aws.toolkits.jetbrains.settings.SamDisplayDevModeWarningSettings
import software.aws.toolkits.jetbrains.settings.SyncSettings
import software.aws.toolkits.jetbrains.settings.relativeSamPath
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyNoActiveCredentialsError
import software.aws.toolkits.jetbrains.utils.notifySamCliNotValidError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.LambdaPackageType
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.SamTelemetry
import software.aws.toolkits.telemetry.SyncedResources

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

            val templateFunctions = SamTemplateUtils.findFunctionsFromTemplate(project, templateFile)
            val hasImageFunctions: Boolean = templateFunctions.any { (it as? SamFunction)?.packageType() == PackageType.IMAGE }
            val lambdaType = if (hasImageFunctions) LambdaPackageType.Image else LambdaPackageType.Zip
            val syncedResourceType = if (codeOnly) SyncedResources.CodeOnly else SyncedResources.AllResources

            val warningSettings = SamDisplayDevModeWarningSettings.getInstance()
            runInEdt {
                if (warningSettings.showDevModeWarning) {
                    if (!SyncServerlessAppWarningDialog(project).showAndGet()) {
                        SamTelemetry.sync(
                            project = project,
                            result = Result.Cancelled,
                            syncedResources = syncedResourceType,
                            lambdaPackageType = lambdaType,
                            version = SamCommon.getVersionString()
                        )
                        return@runInEdt
                    }
                }

                FileDocumentManager.getInstance().saveAllDocuments()
                val parameterDialog = SyncServerlessApplicationDialog(project, templateFile)

                if (!parameterDialog.showAndGet()) {
                    SamTelemetry.sync(
                        project = project,
                        result = Result.Cancelled,
                        syncedResources = syncedResourceType,
                        lambdaPackageType = lambdaType,
                        version = SamCommon.getVersionString()
                    )
                    return@runInEdt
                }
                val settings = parameterDialog.settings()

                saveSettings(project, templateFile, settings)

                if (settings.useContainer) {
                    val checkDocker = runBlocking(getCoroutineBgContext()) {
                        ExecUtil.execAndGetOutput(GeneralCommandLine("docker", "ps"))
                    }
                    if (checkDocker.getStderrLines(true).size != 0) {
                        notifyError(message("docker.not.found"), message("lambda.debug.docker.not_connected"))
                        return@runInEdt
                    }
                }
                syncApp(templateFile, project, settings, syncedResourceType, lambdaType)
            }
        }
    }

    private fun syncApp(
        templateFile: VirtualFile,
        project: Project,
        settings: SyncServerlessApplicationSettings,
        syncedResources: SyncedResources,
        lambdaPackageType: LambdaPackageType
    ) {
        try {
            val templatePath = templateFile.toNioPath()
            val environment = ExecutionEnvironmentBuilder.create(
                project,
                DefaultRunExecutor.getRunExecutorInstance(),
                SyncApplicationRunProfile(project, settings, project.getConnectionSettingsOrThrow(), templatePath, codeOnly)
            ).build()

            environment.runner.execute(environment)
            SamTelemetry.sync(
                project = project,
                result = Result.Succeeded,
                syncedResources = syncedResources,
                lambdaPackageType = lambdaPackageType,
                version = SamCommon.getVersionString()
            )
        } catch (e: Exception) {
            SamTelemetry.sync(
                project = project,
                result = Result.Failed,
                syncedResources = syncedResources,
                lambdaPackageType = lambdaPackageType,
                version = SamCommon.getVersionString()
            )
        }
    }

    private fun saveSettings(project: Project, templateFile: VirtualFile, settings: SyncServerlessApplicationSettings) {
        ModuleUtil.findModuleForFile(templateFile, project)?.let { module ->
            relativeSamPath(module, templateFile)?.let { samPath ->
                SyncSettings.getInstance(module)?.apply {
                    setSamStackName(samPath, settings.stackName)
                    setSamBucketName(samPath, settings.bucket)
                    setSamEcrRepoUri(samPath, settings.ecrRepo)
                    setSamUseContainer(samPath, settings.useContainer)
                    setEnabledCapabilities(samPath, settings.capabilities)
                    setSamTags(samPath, settings.tags)
                    setSamTempParameterOverrides(samPath, settings.parameters)
                }
            }
        }
    }
}
