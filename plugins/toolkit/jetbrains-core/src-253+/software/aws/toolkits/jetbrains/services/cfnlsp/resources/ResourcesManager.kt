// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.TextDocumentIdentifier
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourceNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceRequest
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceSelection
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStatePurpose
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceParams
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CompletableFuture

typealias ResourcesChangeListener = (String, List<String>) -> Unit

@Service(Service.Level.PROJECT)
internal class ResourcesManager(
    private val project: Project
) : Disposable {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }

    private val resourcesByType = mutableMapOf<String, ResourceTypeData>()
    private val loadingTypes = mutableSetOf<String>()
    private val listeners = mutableListOf<ResourcesChangeListener>()

    private data class ResourceTypeData(
        val resourceIdentifiers: List<String>,
        val nextToken: String? = null,
        val loaded: Boolean = false,
    )

    fun addListener(listener: ResourcesChangeListener) {
        listeners.add(listener)
    }

    fun getResourceIdentifiers(resourceType: String): List<String> =
        resourcesByType[resourceType]?.resourceIdentifiers ?: emptyList()

    fun getCachedResources(resourceType: String): List<String>? =
        resourcesByType[resourceType]?.resourceIdentifiers

    fun hasMore(resourceType: String): Boolean =
        resourcesByType[resourceType]?.nextToken != null

    fun isLoaded(resourceType: String): Boolean =
        resourcesByType[resourceType]?.loaded ?: false

    fun reload(resourceType: String) {
        loadResources(resourceType, loadMore = false)
    }

    fun loadMoreResources(resourceType: String) {
        val currentData = resourcesByType[resourceType]
        if (currentData?.nextToken == null) return
        loadResources(resourceType, loadMore = true)
    }

    fun getLoadedResourceTypes(): Set<String> = resourcesByType.keys.toSet()

    fun searchResource(resourceType: String, identifier: String): CompletableFuture<Boolean> {
        LOG.info { "Searching for resource $identifier in type $resourceType" }

        val params = SearchResourceParams(resourceType, identifier)
        return clientServiceProvider().searchResource(params)
            .thenApply { result ->
                if (result?.found == true) {
                    LOG.info { "Resource $identifier found in $resourceType" }
                    
                    if (result.resource != null) {
                        val currentData = resourcesByType[resourceType]
                        val existingResources = currentData?.resourceIdentifiers ?: emptyList()
                        
                        if (!existingResources.contains(identifier)) {
                            val updatedResources = existingResources + identifier
                            resourcesByType[resourceType] = ResourceTypeData(
                                resourceIdentifiers = updatedResources,
                                nextToken = currentData?.nextToken,
                                loaded = true
                            )
                            notifyListeners(resourceType, updatedResources)
                        }
                    } else {
                        if (!isLoaded(resourceType)) {
                            reload(resourceType)
                        }
                    }
                    true
                } else {
                    LOG.info { "Resource $identifier not found in $resourceType" }
                    false
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to search for resource $identifier in $resourceType" }
                false
            }
    }

    fun clear(resourceType: String? = null) {
        if (resourceType != null) {
            resourcesByType.remove(resourceType)
            notifyListeners(resourceType, emptyList())
        } else {
            val types = resourcesByType.keys.toList()
            resourcesByType.clear()
            types.forEach { type ->
                notifyListeners(type, emptyList())
            }
        }
    }

    private fun loadResources(resourceType: String, loadMore: Boolean) {
        if (!loadMore) {
            loadingTypes.add(resourceType)
        }

        LOG.info { "Loading resources for type $resourceType (loadMore=$loadMore)" }

        val currentData = resourcesByType[resourceType]
        val nextToken = if (loadMore) currentData?.nextToken else null

        val params = ListResourcesParams(
            resources = listOf(ResourceRequest(resourceType, nextToken))
        )

        clientServiceProvider().listResources(params)
            .thenAccept { result ->
                loadingTypes.remove(resourceType)
                
                if (result != null) {
                    val resourceSummary = result.resources.firstOrNull { it.typeName == resourceType }
                    if (resourceSummary != null) {
                        LOG.info { "Loaded ${resourceSummary.resourceIdentifiers.size} resources for $resourceType" }

                        val existingResources = if (loadMore) currentData?.resourceIdentifiers ?: emptyList() else emptyList()
                        val allResources = existingResources + resourceSummary.resourceIdentifiers

                        resourcesByType[resourceType] = ResourceTypeData(
                            resourceIdentifiers = allResources,
                            nextToken = resourceSummary.nextToken,
                            loaded = true
                        )

                        notifyListeners(resourceType, allResources)
                    } else {
                        LOG.info { "No resources found for $resourceType" }
                        resourcesByType[resourceType] = ResourceTypeData(
                            resourceIdentifiers = emptyList(),
                            nextToken = null,
                            loaded = true
                        )
                        notifyListeners(resourceType, emptyList())
                    }
                }
            }
            .exceptionally { error ->
                loadingTypes.remove(resourceType)
                LOG.warn(error) { "Failed to load resources for $resourceType" }
                if (!loadMore) {
                    resourcesByType[resourceType] = ResourceTypeData(
                        resourceIdentifiers = emptyList(),
                        nextToken = null,
                        loaded = true
                    )
                }
                null
            }
    }

    private fun notifyListeners(resourceType: String, resources: List<String>) {
        listeners.forEach { it(resourceType, resources) }
    }

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
                        showStackManagementInfo(result)
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

    private fun showStackManagementInfo(result: ResourceStackManagementResult) {
        val messageText = if (result.managedByStack == true) {
            message("cloudformation.explorer.resources.stack_info.managed", result.stackName ?: "Unknown")
        } else {
            message("cloudformation.explorer.resources.stack_info.not_managed")
        }
        
        val actions = mutableListOf<AnAction>()
        
        if (result.managedByStack == true && result.stackName != null) {
            actions.add(object : AnAction(message("cloudformation.explorer.resources.stack_info.copy_name")) {
                override fun actionPerformed(e: AnActionEvent) {
                    CopyPasteManager.getInstance().setContents(StringSelection(result.stackName))
                }
            })
            
            if (result.stackId != null) {
                actions.add(object : AnAction(message("cloudformation.explorer.resources.stack_info.copy_arn")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        CopyPasteManager.getInstance().setContents(StringSelection(result.stackId))
                    }
                })
            }
        }
        
        notifyInfo(
            message("cloudformation.explorer.resources.stack_info.title"),
            messageText,
            project,
            actions
        )
    }

    private fun executeResourceStateOperation(resourceNodes: List<ResourceNode>, purpose: ResourceStatePurpose) {
        // Get active editor
        val fileEditorManager = FileEditorManager.getInstance(project)
        val activeEditor = fileEditorManager.selectedTextEditor
        if (activeEditor == null) {
            LOG.warn { "No active editor found for resource state operation" }
            return
        }

        val document = activeEditor.document
        val virtualFile = fileEditorManager.selectedFiles.firstOrNull()
        if (virtualFile == null) {
            LOG.warn { "No active file found for resource state operation" }
            return
        }

        // Group resources by type
        val resourceSelections = resourceNodes.groupBy { it.resourceType }.map { (resourceType, nodes) ->
            ResourceSelection(resourceType, nodes.map { it.resourceIdentifier })
        }

        LOG.info { "Executing ${purpose.name.lowercase()} operation for ${resourceNodes.size} resources" }

        val params = ResourceStateParams(
            textDocument = TextDocumentIdentifier(virtualFile.url),
            resourceSelections = resourceSelections,
            purpose = purpose.value
        )

        clientServiceProvider().getResourceState(params)
            .thenAccept { result ->
                if (result != null) {
                    // Insert the completion item if provided
                    result.completionItem?.let { completionItem ->
                        ApplicationManager.getApplication().invokeLater {
                            WriteCommandAction.runWriteCommandAction(project) {
                                val insertText = completionItem.insertText ?: completionItem.label
                                val offset = activeEditor.caretModel.offset
                                document.insertString(offset, insertText)
                            }
                        }
                    }

                    // Log results
                    if (result.successfulImports.isNotEmpty()) {
                        LOG.info { "Successfully processed: ${result.successfulImports}" }
                    }
                    if (result.failedImports.isNotEmpty()) {
                        LOG.warn { "Failed to process: ${result.failedImports}" }
                    }
                    result.warning?.let { warning ->
                        LOG.warn { "Warning: $warning" }
                    }
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to execute ${purpose.name.lowercase()} operation" }
                null
            }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        private val LOG = getLogger<ResourcesManager>()
        fun getInstance(project: Project): ResourcesManager = project.service()
    }
}
