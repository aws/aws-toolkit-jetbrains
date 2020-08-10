// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.TagFilter
import software.amazon.awssdk.services.s3.S3Client
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

class ExplorerFilter : AwsExplorerTreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> {
        val resourceNodes = children.filterIsInstance<AwsExplorerResourceNode<*>>()
        val computedNodes = if (resourceNodes.isEmpty()) {
            listOf()
        } else {
            filterByTag(resourceNodes)
        }
        val otherNodes = children.filter { it !is AwsExplorerResourceNode<*> }
        return (otherNodes + computedNodes).toMutableList()
    }

    private fun filterByTag(resourceNodes: List<AwsExplorerResourceNode<*>>): List<AwsExplorerResourceNode<*>> {
        val firstNode = resourceNodes.first()
        val project = firstNode.nodeProject
        val filterManager = ResourceFilterManager.getInstance(project)
        if (!filterManager.tagFilterEnabled()) {
            return resourceNodes
        }
        val client = project.awsClient<ResourceGroupsTaggingApiClient>()
        val tags = client.getResourcesPaginator { request ->
            // S3 is special, bucket resourcetype doesn't exist
            val resourceType = if (firstNode.serviceId == S3Client.SERVICE_NAME) {
                firstNode.serviceId
            } else {
                "${firstNode.serviceId}:${firstNode.resourceType()}"
            }
            filterManager.state.tags.forEach {
                if (it.value.enabled) {
                    request.tagFilters(TagFilter.builder().key(it.key).values(it.value.values).build())
                }
            }
        }.resourceTagMappingList()
        return resourceNodes.filter { node -> tags.any { node.resourceArn() == it.resourceARN() } }
    }
}
