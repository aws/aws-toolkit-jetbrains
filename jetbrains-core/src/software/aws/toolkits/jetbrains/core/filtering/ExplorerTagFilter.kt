// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.filtering

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

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
                val filteredNodes = if (resourceNodes.isEmpty()) {
                    listOf()
                } else {
                    resourceNodes.mapNotNull { node ->
                        val resources = filterManager.getTaggedResources(node.nodeProject, node.serviceId, node.resourceType())
                        if (resources.any { node.resourceArn() == it.resourceARN() }) {
                            node
                        } else {
                            null
                        }
                    }
                }
                val otherNodes = children.filter { it !is AwsExplorerResourceNode<*> }
                (otherNodes + filteredNodes).toMutableList()
            }
        }
        else -> children
    }
}

