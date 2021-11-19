// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.json.JsonFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import software.amazon.awssdk.services.cloudcontrol.CloudControlClient
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.DynamicresourceTelemetry

sealed class DynamicResourceVirtualFile(fileName: String, val dynamicResourceType: String, fileContent: String) :
    LightVirtualFile(
        fileName,
        JsonFileType.INSTANCE,
        fileContent
    )

class CreateDynamicResourceVirtualFile(val connectionSettings: ConnectionSettings, dynamicResourceType: String) :
    DynamicResourceVirtualFile(
        message("dynamic_resources.create_resource_file_name", dynamicResourceType),
        dynamicResourceType,
        InitialCreateDynamicResourceContent.initialContent
    )

class ViewEditableDynamicResourceVirtualFile(val dynamicResourceIdentifier: DynamicResourceIdentifier, fileContent: String) :
    DynamicResourceVirtualFile(
        CloudControlApiResources.getResourceDisplayName(dynamicResourceIdentifier.resourceIdentifier),
        dynamicResourceIdentifier.resourceType,
        fileContent
    )

object InitialCreateDynamicResourceContent {
    const val initialContent = "{}"
}

class DynamicResourceFileManager(private val project: Project) {
    fun openEditor(identifier: DynamicResourceIdentifier, mode: OpenResourceMode) {
        val openFiles = FileEditorManager.getInstance(project).openFiles.filter {
            it is ViewEditableDynamicResourceVirtualFile && it.dynamicResourceIdentifier == identifier
        }
        if (openFiles.isEmpty()) {
            object : Task.Backgroundable(project, message("dynamic_resources.fetch.indicator_title", identifier.resourceIdentifier), true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = message("dynamic_resources.fetch.fetch")
                    val model = getResourceModel(
                        project,
                        identifier.connectionSettings.awsClient(),
                        identifier.resourceType,
                        identifier.resourceIdentifier
                    ) ?: return
                    val file = ViewEditableDynamicResourceVirtualFile(
                        identifier,
                        model
                    )

                    indicator.text = message("dynamic_resources.fetch.open")
                    openFile(project, file, mode, identifier.resourceType)
                }
            }.queue()
        } else {
            val openFile = openFiles.first()
            if (mode == OpenResourceMode.EDIT) {
                openFile.isWritable = true
            }
            FileEditorManager.getInstance(project).openFile(openFile, true)
            EditorNotifications.getInstance(project).updateNotifications(openFile)
        }
    }

    private fun openFile(project: Project, file: ViewEditableDynamicResourceVirtualFile, sourceAction: OpenResourceMode, resourceType: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(PsiUtilCore.getPsiFile(project, file))
            if (sourceAction == OpenResourceMode.READ) {
                file.isWritable = false
                DynamicresourceTelemetry.getResource(project, success = true, resourceType = resourceType)
            } else if (sourceAction == OpenResourceMode.EDIT) {
                file.isWritable = true
            }
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    private fun getResourceModel(project: Project, client: CloudControlClient, resourceType: String, resourceIdentifier: String): String? = try {
        client.getResource { it.typeName(resourceType).identifier(resourceIdentifier) }
            .resourceDescription()
            .properties()
            .also {
                DynamicresourceTelemetry.getResource(project, success = true, resourceType = resourceType)
            }
    } catch (e: Exception) {
        notifyError(
            project = project,
            title = message("dynamic_resources.fetch.fail.title"),
            content = message("dynamic_resources.fetch.fail.content", resourceIdentifier)
        )
        DynamicresourceTelemetry.getResource(project, success = false, resourceType = resourceType)
        null
    }

    companion object {
        fun getInstance(project: Project): DynamicResourceFileManager = project.service()
    }
}

enum class OpenResourceMode {
    READ, EDIT
}
