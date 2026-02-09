// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.ActionGroupOnRightClick
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourcesManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class ResourcesNode(
    nodeProject: Project,
    private val resourceTypesManager: ResourceTypesManager,
    private val resourcesManager: ResourcesManager,
) : AbstractTreeNode<String>(nodeProject, "resources"), ActionGroupOnRightClick {

    override fun actionGroupName(): String = "aws.toolkit.cloudformation.resources.actions"

    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.resources.node"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val selectedTypes = resourceTypesManager.getSelectedResourceTypes()
        return if (selectedTypes.isEmpty()) {
            listOf(AddResourceTypeNode(project, resourceTypesManager))
        } else {
            selectedTypes.map { typeName ->
                ResourceTypeNode(project, typeName, resourcesManager)
            }
        }
    }
}
