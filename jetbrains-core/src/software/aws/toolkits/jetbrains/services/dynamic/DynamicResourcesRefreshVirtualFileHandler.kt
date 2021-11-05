// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.dynamic

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import software.amazon.awssdk.services.cloudcontrol.model.Operation
import software.amazon.awssdk.services.cloudcontrol.model.OperationStatus

class DynamicResourcesRefreshVirtualFileHandler(private val project: Project) : DynamicResourceStateMutationHandler {
    override fun mutationStatusChanged(state: ResourceMutationState) {
        if (state.status == OperationStatus.SUCCESS || state.status == OperationStatus.FAILED) {
            if (state.operation == Operation.UPDATE) {
                refreshViewEditableDynamicResourceVirtualFile(state)
            }
        }
    }

    private fun refreshViewEditableDynamicResourceVirtualFile(state: ResourceMutationState) {
        val file = state.resourceIdentifier?.let { DynamicResourceIdentifier(state.connectionSettings, state.resourceType, it) }?.let {
            CloudControlApiResourcesUtils.getResourceFile(
                project,
                it
            )
        } as? ViewEditableDynamicResourceVirtualFile ?: return
        file.isWritable = true

        runInEdt {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            psiFile?.text?.let { file.setContent(this, it, true) }
        }
    }
}
