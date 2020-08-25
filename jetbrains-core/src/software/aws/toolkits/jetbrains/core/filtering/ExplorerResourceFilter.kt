// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerEmptyNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

/**
 * Filter the AWS explorer by user specified filters. Executed asynchronously after the explorer loads.
 */
class ExplorerResourceFilter : AwsExplorerTreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> = when (parent) {
        is AwsExplorerNode -> {
            val filterManager = FilterManagerService.getInstance(parent.nodeProject)
            if (!filterManager.filtersEnabled()) {
                children
            } else {
                val resourceNodes = children.filterIsInstance<AwsExplorerResourceNode<*>>()
                val filteredNodes = resourceNodes.filter { node -> filterManager.filter(node.resourceArn(), node.serviceId, node.resourceType()) }
                val otherNodes = children.filter { it !is AwsExplorerResourceNode<*> }
                val length = otherNodes.size + filteredNodes.size
                if (length == 0) {
                    // if we filter out all of the nodes, add the empty node. This has to be done manually
                    // since we filter after the parent node builds its children
                    mutableListOf<AbstractTreeNode<*>>(AwsExplorerEmptyNode(parent.nodeProject))
                } else {
                    ArrayList<AbstractTreeNode<*>>(length).apply {
                        addAll(otherNodes)
                        addAll(filteredNodes)
                    }
                }
            }
        }
        else -> children
    }
}
