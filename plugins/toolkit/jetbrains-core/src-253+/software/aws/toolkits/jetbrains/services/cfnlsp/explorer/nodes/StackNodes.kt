// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.AbstractActionTreeNode
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.ActionGroupOnRightClick
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class StacksNode(
    nodeProject: Project,
    private val stacksManager: StacksManager,
    private val changeSetsManager: ChangeSetsManager,
) : AbstractTreeNode<String>(nodeProject, "stacks"), ActionGroupOnRightClick {

    override fun actionGroupName(): String =
        if (stacksManager.hasMore()) {
            "aws.toolkit.cloudformation.stacks.actions.with_more"
        } else {
            "aws.toolkit.cloudformation.stacks.actions"
        }

    override fun update(presentation: PresentationData) {
        val count = if (stacksManager.isLoaded()) {
            val size = stacksManager.get().size
            if (stacksManager.hasMore()) "($size+)" else "($size)"
        } else {
            ""
        }
        presentation.addText(message("cloudformation.explorer.stacks.node_name"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.addText(" $count", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }

    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        if (!stacksManager.isLoaded()) {
            stacksManager.reload()
            return emptyList()
        }

        val stacks = stacksManager.get()

        if (stacks.isEmpty()) {
            return listOf(NoStacksNode(project))
        }

        val nodes = stacks.map { stack ->
            StackNode(project, stack, changeSetsManager)
        }

        return if (stacksManager.hasMore()) {
            nodes + LoadMoreStacksNode(project, stacksManager)
        } else {
            nodes
        }
    }
}

internal class NoStacksNode(nodeProject: Project) : AbstractTreeNode<String>(nodeProject, "no-stacks") {
    override fun update(presentation: PresentationData) {
        presentation.addText("No stacks found", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class LoadMoreStacksNode(
    nodeProject: Project,
    private val stacksManager: StacksManager,
) : AbstractActionTreeNode(nodeProject, "load-more-stacks", AllIcons.General.Add) {

    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.stacks.load_more"), SimpleTextAttributes.LINK_ATTRIBUTES)
        presentation.setIcon(AllIcons.General.Add)
    }

    override fun onDoubleClick(event: java.awt.event.MouseEvent) {
        stacksManager.loadMoreStacks()
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}

internal class StackNode(
    nodeProject: Project,
    val stack: StackSummary,
    private val changeSetsManager: ChangeSetsManager,
) : AbstractTreeNode<StackSummary>(nodeProject, stack), ActionGroupOnRightClick {

    override fun actionGroupName(): String = ACTION_GROUP_NAME

    override fun update(presentation: PresentationData) {
        presentation.addText(stack.stackName ?: "Unknown Stack", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(getStackIcon())
        presentation.tooltip = "${stack.stackName ?: "Unknown"} [${stack.stackStatus ?: "Unknown"}]"
    }

    private fun getStackIcon() = when {
        stack.stackStatus == null -> AllIcons.Nodes.Folder
        stack.stackStatus.contains("COMPLETE") && !stack.stackStatus.contains("ROLLBACK") -> AllIcons.General.InspectionsOK
        stack.stackStatus.contains("FAILED") || stack.stackStatus.contains("ROLLBACK") -> AllIcons.General.Error
        stack.stackStatus.contains("PROGRESS") -> AllIcons.Process.Step_1
        else -> AllIcons.Nodes.Folder
    }

    override fun isAlwaysShowPlus(): Boolean = true

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val stackName = stack.stackName ?: return emptyList()
        return listOf(StackChangeSetsNode(project, stackName, changeSetsManager))
    }

    companion object {
        const val ACTION_GROUP_NAME = "aws.toolkit.cloudformation.stack.actions"
    }
}
