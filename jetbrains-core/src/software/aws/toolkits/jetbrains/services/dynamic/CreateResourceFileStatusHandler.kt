// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.cloudcontrol.CloudControlClient
import software.amazon.awssdk.services.cloudcontrol.model.Operation
import software.amazon.awssdk.services.cloudcontrol.model.OperationStatus
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.DynamicresourceTelemetry

class CreateResourceFileStatusHandler(private val project: Project) : DynamicResourceStateMutationHandler {
    private val resourceCreationProgressTracker: MutableMap<String, VirtualFile> = mutableMapOf()

    init {
        project.messageBus.connect(project).subscribe(DynamicResourceUpdateManager.DYNAMIC_RESOURCE_STATE_CHANGED, this)
    }

    fun recordResourceBeingCreated(token: String, file: VirtualFile) {
        resourceCreationProgressTracker[token] = file
    }

    @TestOnly
    fun getNumberOfResourcesBeingCreated(): Int = resourceCreationProgressTracker.size

    override fun mutationStatusChanged(state: ResourceMutationState) {
        if (state.operation == Operation.CREATE && state.status == OperationStatus.SUCCESS && state.resourceIdentifier != null) {
            runInEdt {
                resourceCreationProgressTracker[state.token]?.let { FileEditorManager.getInstance(project).closeFile(it) }
                resourceCreationProgressTracker.remove(state.token)
            }

            val model = try {
                state.connectionSettings.awsClient<CloudControlClient>()
                    .getResource {
                        it.typeName(state.resourceType)
                        it.identifier(state.resourceIdentifier)
                    }
                    .resourceDescription()
                    .properties()
            } catch (e: Exception) {
                notifyError(
                    project = project,
                    title = message("dynamic_resources.fetch.fail.title"),
                    content = message("dynamic_resources.fetch.fail.content", state.resourceIdentifier)
                )
                DynamicresourceTelemetry.getResource(project, success = false, resourceType = state.resourceType)
                null
            } ?: return

            val dynamicResourceIdentifier = DynamicResourceIdentifier(state.connectionSettings, state.resourceType, state.resourceIdentifier)
            val file = ViewEditableDynamicResourceVirtualFile(
                dynamicResourceIdentifier,
                model
            )

            WriteCommandAction.runWriteCommandAction(project) {
                CodeStyleManager.getInstance(project).reformat(PsiUtilCore.getPsiFile(project, file))
                file.isWritable = false
                DynamicresourceTelemetry.getResource(project, success = true, resourceType = state.resourceType)
                FileEditorManager.getInstance(project).openFile(file, true)
            }
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<CreateResourceFileStatusHandler>()
    }
}
