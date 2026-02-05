// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import icons.AwsIcons
import software.aws.toolkit.core.utils.error
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.AbstractActionTreeNode
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ResourceTypeSelectionDialog
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.ActionGroupOnRightClick
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourcesManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.event.MouseEvent

internal class ResourceTypeNode(
    nodeProject: Project,
    val resourceType: String,
    private val resourcesManager: ResourcesManager,
) : AbstractTreeNode<String>(nodeProject, resourceType), ActionGroupOnRightClick {

    override fun actionGroupName(): String = "aws.toolkit.cloudformation.resources.type.actions"

    override fun isAlwaysShowPlus(): Boolean = true

    override fun update(presentation: PresentationData) {
        presentation.addText(resourceType, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(null) // Remove any default icon

        // Only show count if resources have been loaded (dropdown was expanded)
        if (resourcesManager.isLoaded(resourceType)) {
            val resources = resourcesManager.getCachedResources(resourceType)
            if (resources != null) {
                val hasMore = resourcesManager.hasMore(resourceType)
                val countText = if (hasMore) " (${resources.size}+)" else " (${resources.size})"
                presentation.addText(countText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        if (!resourcesManager.isLoaded(resourceType)) {
            // Trigger loading when this node is expanded
            resourcesManager.reload(resourceType)
            return listOf(LoadingResourcesNode(project, resourceType))
        }

        val resources = resourcesManager.getResourceIdentifiers(resourceType)

        if (resources.isEmpty()) {
            return listOf(NoResourcesNode(project, resourceType))
        }

        val nodes = resources.map { identifier ->
            ResourceNode(project, resourceType, identifier)
        }

        return if (resourcesManager.hasMore(resourceType)) {
            nodes + LoadMoreResourcesNode(project, resourceType, resourcesManager)
        } else {
            nodes
        }
    }
}

internal class LoadingResourcesNode(
    nodeProject: Project,
    private val resourceType: String,
) : AbstractTreeNode<String>(nodeProject, "loading") {

    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.resources.loading", resourceType), SimpleTextAttributes.GRAYED_ATTRIBUTES)
        presentation.setIcon(AllIcons.Process.Step_1)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class NoResourcesNode(
    nodeProject: Project,
    private val resourceType: String,
) : AbstractTreeNode<String>(nodeProject, "no-resources") {

    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.resources.no_resources", resourceType), SimpleTextAttributes.GRAYED_ATTRIBUTES)
        presentation.setIcon(AllIcons.General.Settings)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class LoadMoreResourcesNode(
    nodeProject: Project,
    private val resourceType: String,
    private val resourcesManager: ResourcesManager,
) : AbstractActionTreeNode(nodeProject, message("cloudformation.explorer.resources.load_more"), AllIcons.General.Add) {

    override fun onDoubleClick(event: MouseEvent) {
        resourcesManager.loadMoreResources(resourceType)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class ResourceNode(
    nodeProject: Project,
    val resourceType: String,
    val resourceIdentifier: String,
) : AbstractTreeNode<String>(nodeProject, resourceIdentifier), ActionGroupOnRightClick {
    override fun actionGroupName(): String = "aws.toolkit.cloudformation.resources.resource.actions"

    override fun update(presentation: PresentationData) {
        presentation.addText(resourceIdentifier, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.tooltip = "$resourceType: $resourceIdentifier"
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class AddResourceTypeNode(
    nodeProject: Project,
    private val resourceTypesManager: software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager,
) : AbstractActionTreeNode(nodeProject, message("cloudformation.explorer.resources.add_type_node"), AllIcons.General.Add) {
    companion object {
        private val LOG = getLogger<AddResourceTypeNode>()
    }

    override fun onDoubleClick(event: MouseEvent) {
        // Always load types (in case region changed)
        resourceTypesManager.loadAvailableTypes().thenRun {
            LOG.info { "loading completed, showing dialog" }
            ApplicationManager.getApplication().invokeLater {
                showDialog()
            }
        }
    }

    private fun showDialog() {
        val availableTypes = resourceTypesManager.getAvailableResourceTypes()
        val selectedTypes = resourceTypesManager.getSelectedResourceTypes()
        // Show ALL available types, not just unselected ones
        
        if (availableTypes.isEmpty()) {
            return
        }

        LOG.info { "starting dialog" }
        try {
            val dialog = ResourceTypeSelectionDialog(project, availableTypes, selectedTypes)
            if (dialog.showAndGet()) {
                // Handle both additions and removals
                val newSelections = dialog.selectedResourceTypes.toSet()
                val currentSelections = selectedTypes
                
                // Add new selections
                newSelections.forEach { type ->
                    if (type !in currentSelections) {
                        resourceTypesManager.addResourceType(type)
                    }
                }
                
                // Remove deselected types
                currentSelections.forEach { type ->
                    if (type !in newSelections) {
                        resourceTypesManager.removeResourceType(type)
                    }
                }
                
                LOG.info { "finished updating resource types" }
            }
        } catch (e: Exception) {
            LOG.error(e) { "Failed to show dialog" }
        }
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}
