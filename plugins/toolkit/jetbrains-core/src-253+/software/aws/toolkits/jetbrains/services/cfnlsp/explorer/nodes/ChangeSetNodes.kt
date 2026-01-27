// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.AbstractActionTreeNode
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class StackChangeSetsNode(
    nodeProject: Project,
    private val stackName: String,
    private val changeSetsManager: ChangeSetsManager,
) : AbstractTreeNode<String>(nodeProject, "changesets-$stackName") {

    override fun update(presentation: PresentationData) {
        val changeSets = changeSetsManager.get(stackName)
        val hasMore = changeSetsManager.hasMore(stackName)
        val countText = if (hasMore) "(${changeSets.size}+)" else "(${changeSets.size})"
        presentation.addText(message("cloudformation.explorer.change_sets"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.addText(" $countText", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }

    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        if (!changeSetsManager.isLoaded(stackName)) {
            changeSetsManager.fetchChangeSets(stackName)
            return emptyList()
        }

        val changeSets = changeSetsManager.get(stackName)
        if (changeSets.isEmpty()) {
            return listOf(NoChangeSetsNode(project))
        }

        val nodes = changeSets.map { changeSet ->
            ChangeSetNode(project, changeSet.changeSetName, changeSet.status)
        }

        return if (changeSetsManager.hasMore(stackName)) {
            nodes + LoadMoreChangeSetsNode(project, stackName, changeSetsManager)
        } else {
            nodes
        }
    }
}

internal class NoChangeSetsNode(nodeProject: Project) : AbstractTreeNode<String>(nodeProject, "no-changesets") {
    override fun update(presentation: PresentationData) {
        presentation.addText("No change sets found", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class LoadMoreChangeSetsNode(
    nodeProject: Project,
    private val stackName: String,
    private val changeSetsManager: ChangeSetsManager,
) : AbstractActionTreeNode(nodeProject, "load-more-changesets-$stackName", AllIcons.General.Add) {

    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.load_more"), SimpleTextAttributes.LINK_ATTRIBUTES)
        presentation.setIcon(AllIcons.General.Add)
    }

    override fun onDoubleClick(event: java.awt.event.MouseEvent) {
        changeSetsManager.loadMoreChangeSets(stackName)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class ChangeSetNode(
    nodeProject: Project,
    private val changeSetName: String,
    private val status: String,
) : AbstractTreeNode<String>(nodeProject, changeSetName) {

    override fun update(presentation: PresentationData) {
        presentation.addText(changeSetName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.addText(" [$status]", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}
