// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourceNode
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourceTypeNode
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourcesNode
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceStateService
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ResourceTypeDialogUtils
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.awt.datatransfer.StringSelection

class AddResourceTypeAction : AnAction(
    message("cloudformation.explorer.resources.add_type"),
    null,
    AllIcons.General.Add
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        // Only enable if a ResourcesNode is selected
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourcesNode = selectedNodes?.filterIsInstance<ResourcesNode>()?.isNotEmpty() == true
        e.presentation.isEnabled = hasResourcesNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val resourceTypesManager = ResourceTypesManager.getInstance(project)

        // Always load types (in case region changed)
        resourceTypesManager.loadAvailableTypes().thenRun {
            LOG.info { "loading completed, showing dialog" }
            ApplicationManager.getApplication().invokeLater {
                showDialog(project, resourceTypesManager)
            }
        }
    }

    private fun showDialog(project: Project, resourceTypesManager: ResourceTypesManager) {
        ResourceTypeDialogUtils.showResourceTypeSelectionDialog(project, resourceTypesManager)
    }

    companion object {
        private val LOG = getLogger<AddResourceTypeAction>()
    }
}

class RemoveResourceTypeAction : AnAction(
    message("cloudformation.explorer.resources.remove_type"),
    null,
    AllIcons.General.Remove
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        // Only enable if a ResourceTypeNode is selected
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourceTypeNode = selectedNodes?.filterIsInstance<ResourceTypeNode>()?.isNotEmpty() == true
        e.presentation.isEnabled = hasResourceTypeNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val resourceTypesManager = ResourceTypesManager.getInstance(project)
        val resourceLoader = ResourceLoader.getInstance(project)

        // Get the selected ResourceTypeNode
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val resourceTypeNode = selectedNodes?.filterIsInstance<ResourceTypeNode>()?.firstOrNull() ?: return

        // Remove the resource type
        resourceTypesManager.removeResourceType(resourceTypeNode.resourceType)
        resourceLoader.clear(resourceTypeNode.resourceType)
    }
}

class RefreshResourceTypeAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.text = message("cloudformation.explorer.resources.refresh_type")
        e.presentation.icon = AllIcons.Actions.Refresh
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info { "RefreshResourceTypeAction triggered" }
        val project = e.project ?: return
        val resourceLoader = ResourceLoader.getInstance(project)

        // Get the selected nodes using the correct data key
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)

        // Find ResourceTypeNode in the selection
        val resourceTypeNode = selectedNodes?.filterIsInstance<ResourceTypeNode>()?.firstOrNull()

        if (resourceTypeNode != null) {
            LOG.info { "Reloading resource type: ${resourceTypeNode.resourceType}" }
            resourceLoader.refreshResources(resourceTypeNode.resourceType)
        } else {
            LOG.warn { "No ResourceTypeNode found in selection" }
        }
    }
    companion object {
        private val LOG = getLogger<RefreshResourceTypeAction>()
    }
}

class RefreshAllLoadedResourcesAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.text = message("cloudformation.explorer.resources.refresh_all_loaded")
        e.presentation.icon = AllIcons.Actions.Refresh
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val resourceLoader = ResourceLoader.getInstance(project)

        // Get all currently loaded resource types and reload them
        val loadedTypes = resourceLoader.getLoadedResourceTypes()
        loadedTypes.forEach { resourceType ->
            resourceLoader.refreshResources(resourceType)
        }
    }
}

class SearchResourceAction : AnAction(
    message("cloudformation.explorer.resources.search"),
    null,
    AllIcons.Actions.Search
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        // Only enable if a ResourceTypeNode is selected
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourceTypeNode = selectedNodes?.filterIsInstance<ResourceTypeNode>()?.isNotEmpty() == true
        e.presentation.isEnabled = hasResourceTypeNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val resourceLoader = ResourceLoader.getInstance(project)

        // Get the selected ResourceTypeNode
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val resourceTypeNode = selectedNodes?.filterIsInstance<ResourceTypeNode>()?.firstOrNull() ?: return

        // Prompt user for resource identifier
        val identifier = Messages.showInputDialog(
            project,
            message("cloudformation.explorer.resources.search.prompt", resourceTypeNode.resourceType),
            message("cloudformation.explorer.resources.search.title"),
            AllIcons.Actions.Search
        ) ?: return

        if (identifier.isBlank()) return

        // Search for the resource
        resourceLoader.searchResource(resourceTypeNode.resourceType, identifier.trim())
    }
}

class ImportResourceStateAction : AnAction(
    message("cloudformation.explorer.resources.import"),
    null,
    AllIcons.Actions.Download
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourceNode = selectedNodes?.filterIsInstance<ResourceNode>()?.isNotEmpty() == true
        e.presentation.isEnabled = hasResourceNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stateService = ResourceStateService.getInstance(project)

        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val resourceNodes = selectedNodes?.filterIsInstance<ResourceNode>() ?: return

        stateService.importResourceState(resourceNodes)
    }
}

class CloneResourceStateAction : AnAction(
    message("cloudformation.explorer.resources.clone"),
    null,
    AllIcons.Vcs.Clone
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourceNode = selectedNodes?.filterIsInstance<ResourceNode>()?.isNotEmpty() == true
        e.presentation.isEnabled = hasResourceNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stateService = ResourceStateService.getInstance(project)

        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val resourceNodes = selectedNodes?.filterIsInstance<ResourceNode>() ?: return

        stateService.cloneResourceState(resourceNodes)
    }
}

class CopyResourceIdentifierAction : AnAction(
    message("cloudformation.explorer.resources.copy_identifier"),
    null,
    AllIcons.Actions.Copy
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourceNode = selectedNodes?.filterIsInstance<ResourceNode>()?.size == 1
        e.presentation.isEnabled = hasResourceNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val resourceNode = selectedNodes?.filterIsInstance<ResourceNode>()?.firstOrNull() ?: return

        // Copy to clipboard
        val selection = StringSelection(resourceNode.resourceIdentifier)
        CopyPasteManager.getInstance().setContents(selection)

        // Show status message (similar to VS Code)
        // Note: JetBrains doesn't have a direct equivalent to VS Code's status bar message
        // but the copy operation will be visible in the clipboard
    }
}

class GetStackManagementInfoAction : AnAction(
    message("cloudformation.explorer.resources.stack_info"),
    null,
    AllIcons.Actions.Properties
) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val hasResourceNode = selectedNodes?.filterIsInstance<ResourceNode>()?.size == 1
        e.presentation.isEnabled = hasResourceNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stateService = ResourceStateService.getInstance(project)

        val selectedNodes = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)
        val resourceNode = selectedNodes?.filterIsInstance<ResourceNode>()?.firstOrNull() ?: return

        stateService.getStackManagementInfo(resourceNode)
    }
}
