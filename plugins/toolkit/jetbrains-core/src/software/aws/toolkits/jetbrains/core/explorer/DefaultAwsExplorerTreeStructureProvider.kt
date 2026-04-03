// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.ide.util.treeView.AbstractTreeNode

class DefaultAwsExplorerTreeStructureProvider : AwsExplorerTreeStructureProvider() {
    override fun modify(parent: AbstractTreeNode<*>, children: MutableCollection<AbstractTreeNode<*>>): MutableCollection<AbstractTreeNode<*>> =
        children.sortedWith(
            compareBy<AbstractTreeNode<*>> { it !is PinnedFirstNode }
                .thenComparing(compareBy(String.CASE_INSENSITIVE_ORDER) { it.toString() })
        ).toMutableList()
}

/**
 * Marker interface for nodes that should always appear first in alphabetically sorted lists.
 */
interface PinnedFirstNode
