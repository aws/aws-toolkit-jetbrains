// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.ide.util.treeView.AbstractTreeNode

interface AwsExplorerFilter {
    fun displayName(): String
    fun modify(parent: AbstractTreeNode<*>, children: Collection<AbstractTreeNode<*>>): Collection<AbstractTreeNode<*>>
}
