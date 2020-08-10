// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.services.resourcegroupstaggingapi.resources.ResourceGroupsTaggingApiResources

class ExplorerTagFilter : AwsExplorerTreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> = when (parent) {
        is AwsExplorerNode -> {
            val filterManager = ResourceFilterManager.getInstance(parent.nodeProject)
            if (!filterManager.tagFilterEnabled()) {
                children
            } else {
                val resourceNodes = children.filterIsInstance<AwsExplorerResourceNode<*>>()
                val computedNodes = if (resourceNodes.isEmpty()) {
                    listOf()
                } else {
                    filterByTag(parent.nodeProject, filterManager, resourceNodes)
                }
                val otherNodes = children.filter { it !is AwsExplorerResourceNode<*> }
                (otherNodes + computedNodes).toMutableList()
            }
        }
        else -> children
    }
}

private fun filterByTag(
    project: Project,
    filterManager: ResourceFilterManager,
    resourceNodes: List<AwsExplorerResourceNode<*>>
): List<AwsExplorerResourceNode<*>> {
    val resourceCache = AwsResourceCache.getInstance(project)
    return resourceNodes.mapNotNull { node ->
        val tags = resourceCache
            .getResourceNow(
                resource = ResourceGroupsTaggingApiResources.listResources(node.serviceId, node.resourceType(), filterManager.state.tags),
                region = node.nodeProject.activeRegion(),
                credentialProvider = node.nodeProject.activeCredentialProvider()
            )
            .resourceTagMappingList()
        if (tags.any { node.resourceArn() == it.resourceARN() }) {
            node
        } else {
            null
        }
    }
}
