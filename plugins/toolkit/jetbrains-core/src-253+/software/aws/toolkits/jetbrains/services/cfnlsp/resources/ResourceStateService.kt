// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.TextDocumentIdentifier
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourceNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceSelection
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStatePurpose
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

@Service(Service.Level.PROJECT)
internal class ResourceStateService(
    private val project: Project,
) {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }
    internal var editor = ResourceStateEditor.getInstance(project)
    private val notificationService = ResourceNotificationService(project)

    fun importResourceState(resourceNodes: List<ResourceNode>) {
        executeResourceStateOperation(resourceNodes, ResourceStatePurpose.IMPORT)
    }

    fun cloneResourceState(resourceNodes: List<ResourceNode>) {
        executeResourceStateOperation(resourceNodes, ResourceStatePurpose.CLONE)
    }

    fun getStackManagementInfo(resourceNode: ResourceNode) {
        clientServiceProvider().getStackManagementInfo(resourceNode.resourceIdentifier)
            .thenAccept { result ->
                LOG.info { "Stack management info result for ${resourceNode.resourceIdentifier}: $result" }

                ApplicationManager.getApplication().invokeLater {
                    if (result != null) {
                        notificationService.showStackManagementInfo(result)
                    } else {
                        LOG.warn { "Received null result from stack management info request" }
                    }
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to get stack management info for resource: ${resourceNode.resourceIdentifier}" }
                ApplicationManager.getApplication().invokeLater {
                    notifyError(
                        message("cloudformation.explorer.resources.stack_info.error"),
                        error.message ?: "Unknown error"
                    )
                }
                null
            }
    }

    private fun executeResourceStateOperation(resourceNodes: List<ResourceNode>, purpose: ResourceStatePurpose) {
        if (editor.getActiveEditor() == null) {
            LOG.warn { "No active editor found for resource state operation" }
            notifyError(
                message("cloudformation.explorer.resources.${purpose.name.lowercase()}").removeSuffix(" Resource State"),
                "Open a CloudFormation template to author resource state",
                project
            )
            return
        }

        val documentUri = editor.getActiveDocumentUri()
        if (documentUri == null) {
            LOG.warn { "No active file found for resource state operation" }
            notifyError(
                message("cloudformation.explorer.resources.${purpose.name.lowercase()}").removeSuffix(" Resource State"),
                "No active file found",
                project
            )
            return
        }

        val resourceSelections = resourceNodes.groupBy { it.resourceType }.map { (resourceType, nodes) ->
            ResourceSelection(resourceType, nodes.map { it.resourceIdentifier })
        }

        LOG.info { "Executing ${purpose.name.lowercase()} operation for ${resourceNodes.size} resources" }

        val params = ResourceStateParams(
            textDocument = TextDocumentIdentifier(documentUri),
            resourceSelections = resourceSelections,
            purpose = purpose.value
        )

        clientServiceProvider().getResourceState(params)
            .thenAccept { result ->
                if (result != null) {
                    result.warning?.let { warning ->
                        LOG.warn { "Warning: $warning" }
                        ApplicationManager.getApplication().invokeLater {
                            notifyError(
                                message("cloudformation.explorer.resources.${purpose.name.lowercase()}").removeSuffix(" Resource State"),
                                warning,
                                project
                            )
                        }
                    }

                    result.completionItem?.let { completionItem ->
                        val insertText = completionItem.insertText ?: completionItem.label
                        editor.insertAtCaret(insertText)
                    }

                    val successCount = result.successfulImports.values.sumOf { it.size }
                    val failureCount = result.failedImports.values.sumOf { it.size }

                    ApplicationManager.getApplication().invokeLater {
                        notificationService.showResultNotification(successCount, failureCount, purpose)
                    }

                    if (result.successfulImports.isNotEmpty()) {
                        LOG.info { "Successfully processed: ${result.successfulImports}" }
                    }
                    if (result.failedImports.isNotEmpty()) {
                        LOG.warn { "Failed to process: ${result.failedImports}" }
                    }
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to execute ${purpose.name.lowercase()} operation" }
                ApplicationManager.getApplication().invokeLater {
                    notifyError(
                        message("cloudformation.explorer.resources.${purpose.name.lowercase()}").removeSuffix(" Resource State"),
                        "Failed to ${purpose.name.lowercase()} resources: ${error.message ?: "Unknown error"}",
                        project
                    )
                }
                null
            }
    }

    companion object {
        private val LOG = getLogger<ResourceStateService>()
        fun getInstance(project: Project): ResourceStateService = project.service()
    }
}
