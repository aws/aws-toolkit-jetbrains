// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.AbstractActionTreeNode
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.ActionGroupOnRightClick
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ResourceTypeDialogUtils
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.event.MouseEvent

internal class ResourceTypeNode(
    nodeProject: Project,
    val resourceType: String,
    private val resourceLoader: ResourceLoader,
) : AbstractTreeNode<String>(nodeProject, resourceType), ActionGroupOnRightClick {

    override fun actionGroupName(): String = ACTION_GROUP_NAME

    override fun isAlwaysShowPlus(): Boolean = true

    override fun update(presentation: PresentationData) {
        presentation.addText(resourceType, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(null) // Remove any default icon

        // Only show count if resources have been loaded (dropdown was expanded)
        if (resourceLoader.isLoaded(resourceType)) {
            val resources = resourceLoader.getCachedResources(resourceType)
            if (resources != null) {
                val hasMore = resourceLoader.hasMore(resourceType)
                val countText = if (hasMore) " (${resources.size}+)" else " (${resources.size})"
                presentation.addText(countText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        if (!resourceLoader.isLoaded(resourceType)) {
            // Trigger loading when this node is expanded
            resourceLoader.refreshResources(resourceType)
            return listOf(LoadingResourcesNode(project, resourceType))
        }

        val resources = resourceLoader.getResourceIdentifiers(resourceType)

        if (resources.isEmpty()) {
            return listOf(NoResourcesNode(project))
        }

        val nodes = resources.map { identifier ->
            ResourceNode(project, resourceType, identifier)
        }

        return if (resourceLoader.hasMore(resourceType)) {
            nodes + LoadMoreResourcesNode(project, resourceType, resourceLoader)
        } else {
            nodes
        }
    }

    companion object {
        private const val ACTION_GROUP_NAME = "aws.toolkit.cloudformation.resources.type.actions"
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
) : AbstractTreeNode<String>(nodeProject, "no-resources") {

    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.resources.no_resources"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class LoadMoreResourcesNode(
    nodeProject: Project,
    private val resourceType: String,
    private val resourceLoader: ResourceLoader,
) : AbstractActionTreeNode(nodeProject, message("cloudformation.explorer.resources.load_more"), AllIcons.General.Add) {

    override fun onDoubleClick(event: MouseEvent) {
        resourceLoader.loadMoreResources(resourceType)
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
    private val resourceTypesManager: ResourceTypesManager,
) : AbstractActionTreeNode(nodeProject, message("cloudformation.explorer.resources.add_type_node"), AllIcons.General.Add) {
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
        ResourceTypeDialogUtils.showResourceTypeSelectionDialog(project, resourceTypesManager)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true

    companion object {
        private val LOG = getLogger<AddResourceTypeNode>()
    }
}
