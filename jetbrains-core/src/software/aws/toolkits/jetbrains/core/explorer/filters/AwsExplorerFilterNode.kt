// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceActionNode

class AwsExplorerFilterNode(project: Project, private val filter: AwsExplorerFilter) :
    AwsExplorerNode<AwsExplorerFilter>(project, filter, AllIcons.General.Filter), ResourceActionNode {
    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()
    override fun actionGroupName(): String = "aws.toolkit.explorer.filter"
    override fun toString() = filter.displayName()
    override fun displayName() = filter.displayName()
}
