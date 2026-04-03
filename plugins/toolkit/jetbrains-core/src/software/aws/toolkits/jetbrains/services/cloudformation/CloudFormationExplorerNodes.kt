// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import icons.AwsIcons
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkit.jetbrains.utils.toHumanReadable
import software.aws.toolkits.jetbrains.core.explorer.AwsToolkitExplorerToolWindow
import software.aws.toolkits.jetbrains.core.explorer.PinnedFirstNode
import software.aws.toolkits.jetbrains.core.explorer.ToolkitToolWindowTab
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerServiceNode
import software.aws.toolkits.jetbrains.core.explorer.nodes.CacheBackedAwsExplorerServiceRootNode
import software.aws.toolkits.jetbrains.services.cloudformation.resources.CloudFormationResources
import software.aws.toolkits.jetbrains.services.cloudformation.stack.StackWindowManager
import software.aws.toolkits.resources.AwsToolkitBundle.message
import javax.swing.JComponent

class CloudFormationServiceNode(project: Project, service: AwsExplorerServiceNode) : CacheBackedAwsExplorerServiceRootNode<StackSummary>(
    project,
    service,
    CloudFormationResources.ACTIVE_STACKS
) {
    override fun displayName(): String = message("explorer.node.cloudformation")
    override fun toNode(child: StackSummary): AwsExplorerNode<*> = CloudFormationStackNode(nodeProject, child.stackName(), child.stackStatus(), child.stackId())

    override fun getChildren(): List<AwsExplorerNode<*>> {
        val hasCfnPanel = ToolkitToolWindowTab.EP_NAME.extensionList.any {
            it.tabId == message("cloudformation.explorer.tab.title") && it.enabled()
        }
        return if (hasCfnPanel) {
            listOf(TryCloudFormationPanelNode(nodeProject)) + super.getChildren()
        } else {
            super.getChildren()
        }
    }
}

class CloudFormationStackNode(
    project: Project,
    val stackName: String,
    private val stackStatus: StackStatus,
    val stackId: String,
) : AwsExplorerResourceNode<String>(
    project,
    CloudFormationClient.SERVICE_NAME,
    stackName,
    AwsIcons.Resources.CLOUDFORMATION_STACK
) {
    override fun resourceType() = "stack"

    override fun resourceArn() = stackId

    override fun displayName() = stackName

    override fun statusText(): String = stackStatus.toString().toHumanReadable()

    override fun onDoubleClick() {
        StackWindowManager.getInstance(nodeProject).openStack(stackName, stackId)
    }
}

class TryCloudFormationPanelNode(project: Project) :
    AwsExplorerNode<String>(project, "try-cfn-panel", AllIcons.Nodes.Favorite), PinnedFirstNode {

    override fun getChildren(): List<AwsExplorerNode<*>> = emptyList()

    override fun isAlwaysLeaf(): Boolean = true

    override fun displayName(): String = message("cloudformation.explorer.try_new_panel")

    override fun update(presentation: PresentationData) {
        presentation.setIcon(AllIcons.Nodes.Favorite)
        presentation.addText(displayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        presentation.tooltip = message("cloudformation.explorer.try_new_panel.tooltip")
    }

    override fun onDoubleClick() {
        val toolWindow = AwsToolkitExplorerToolWindow.toolWindow(nodeProject)
        toolWindow.activate {
            val explorerWindow = AwsToolkitExplorerToolWindow.getInstance(nodeProject)
            val tabComponent = explorerWindow.selectTab(message("cloudformation.explorer.tab.title"))
            (tabComponent as? JComponent)?.let { component ->
                UIUtil.findComponentOfType(component, Tree::class.java)?.let { tree ->
                    TreeUtil.expand(tree, 1)
                }
            }
        }
    }
}
