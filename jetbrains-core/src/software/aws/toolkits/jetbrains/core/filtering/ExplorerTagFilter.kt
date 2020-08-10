// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources

class ExplorerTagFilter : AwsExplorerTreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> {
        val awsParent = parent as? AwsExplorerNode<*> ?: return children
        val filterManager = ResourceFilterManager.getInstance(awsParent.nodeProject)
        if (!filterManager.tagFilterEnabled()) {
            return children
        }
        val resourceNodes = children.filterIsInstance<AwsExplorerResourceNode<*>>()
        val computedNodes = if (resourceNodes.isEmpty()) {
            listOf()
        } else {
            filterByTag(awsParent.nodeProject, filterManager, resourceNodes)
        }
        val otherNodes = children.filter { it !is AwsExplorerResourceNode<*> }
        return (otherNodes + computedNodes).toMutableList()
    }

    private fun filterByTag(
        project: Project,
        filterManager: ResourceFilterManager,
        resourceNodes: List<AwsExplorerResourceNode<*>>
    ): List<AwsExplorerResourceNode<*>> {
        val resourceCache = AwsResourceCache.getInstance(project)
        return resourceNodes.mapNotNull { node ->
            val tags = resourceCache
                .getResourceNow(ResourceGroupsTaggingApiResources.listResources(node.serviceId, node.resourceType(), filterManager.state.tags))
                .resourceTagMappingList()
            if (tags.any { node.resourceArn() == it.resourceARN() }) {
                node
            } else {
                null
            }
        }
    }
}
