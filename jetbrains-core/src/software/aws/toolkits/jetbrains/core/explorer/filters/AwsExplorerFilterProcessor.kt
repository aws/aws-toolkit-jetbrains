// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.ide.util.treeView.AbstractTreeNode
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerRootNode

class AwsExplorerFilterProcessor : AwsExplorerTreeStructureProvider() {
    override fun modify(parent: AbstractTreeNode<*>, children: MutableCollection<AbstractTreeNode<*>>): MutableCollection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children
        val filter = AwsExplorerFilterManager.getInstance(project).currentFilter() ?: return children

        val modifiedChildren = mutableListOf<AbstractTreeNode<*>>()
        if (parent is AwsExplorerRootNode) {
            modifiedChildren.add(AwsExplorerFilterNode(project, filter))
        }

        modifiedChildren.addAll(filter.modify(parent, children))

        return modifiedChildren
    }
}
