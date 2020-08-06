// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.TagFilter
import software.aws.toolkits.jetbrains.core.ResourceFilterManager
import software.aws.toolkits.jetbrains.core.awsClient

class ExplorerFilter : AwsExplorerTreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> {
        return children /*
        val client = nodeProject.awsClient<ResourceGroupsTaggingApiClient>()
        val children = getChildrenInternal()
        val tags = client.getResourcesPaginator { request ->
            request.resourceTypeFilters("${serviceId()}:${resourceType()}")
            ResourceFilterManager.getInstance(nodeProject).getActiveFilters().forEach {
                request.tagFilters(TagFilter.builder().key(it.key).values(it.value).build())
            }
        }.resourceTagMappingList()
        children.filter { node -> tags.any { node.resourceArn() == it.resourceARN() } }
        return children*/
    }
}
