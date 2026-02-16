// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VfsUtil
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StackNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ValidateAndDeployDialog
import java.io.File
import java.util.UUID

internal class ValidateAndDeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val selectedNode = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)?.firstOrNull()
        val templateFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedEditor?.file?.takeIf {
                it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
            }

        val prefilledTemplate = templateFile?.path
        val prefilledStackName = (selectedNode as? StackNode)?.stack?.stackName

        val dialog = ValidateAndDeployDialog(
            project = project,
            prefilledTemplatePath = prefilledTemplate,
            prefilledStackName = prefilledStackName,
        )

        if (!dialog.showAndGet()) return

        val settings = dialog.getSettings()
        val templateVFile = VfsUtil.findFileByIoFile(File(settings.templatePath), true) ?: return

        val clientService = CfnClientService.getInstance(project)
        clientService.ensureDocumentOpen(templateVFile, project)

        val descriptor = CfnLspServerDescriptor.getInstance(project)
        val params = CreateValidationParams(
            id = UUID.randomUUID().toString(),
            uri = descriptor.getFileUri(templateVFile),
            stackName = settings.stackName,
            keepChangeSet = true,
        )

        ValidationWorkflow(project).validate(params)
    }
}
