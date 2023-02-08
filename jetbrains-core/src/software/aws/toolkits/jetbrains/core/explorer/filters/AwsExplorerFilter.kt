// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.filters

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import software.aws.toolkits.jetbrains.core.explorer.AwsExplorerTreeStructureProvider
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerRootNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.ResourceActionNode
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree

interface AwsExplorerFilter {
    fun displayName(): String
    fun modify(parent: AbstractTreeNode<*>, children: Collection<AbstractTreeNode<*>>): Collection<AbstractTreeNode<*>>
}

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

class AwsExplorerFilterNode(project: Project, private val filter: AwsExplorerFilter) :
    AwsExplorerNode<AwsExplorerFilter>(project, filter, AllIcons.General.Filter), ResourceActionNode {
    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()
    override fun actionGroupName(): String = "aws.toolkit.explorer.filter"

    override fun toString() = filter.displayName()
    override fun displayName() = filter.displayName()
}

class AwsExplorerFilterClear : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let {
            AwsExplorerFilterManager.getInstance(it).clearFilter()
        }
    }
}

class AwsExplorerFilterManager(private val project: Project) {

    private var currentFilter: AwsExplorerFilter? = null
    fun setFilter(filter: AwsExplorerFilter) {
        currentFilter = filter
        project.refreshAwsTree()
    }

    fun clearFilter() {
        val filter = currentFilter
        if (filter is Disposable) {
            Disposer.dispose(filter)
        }
        currentFilter = null
        project.refreshAwsTree()
    }

    fun currentFilter(): AwsExplorerFilter? = currentFilter

    companion object {
        fun getInstance(project: Project): AwsExplorerFilterManager = project.service()
    }
}
