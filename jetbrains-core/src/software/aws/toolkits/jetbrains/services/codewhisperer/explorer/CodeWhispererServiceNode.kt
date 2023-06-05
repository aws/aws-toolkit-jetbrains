// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.explorer

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.AbstractActionTreeNode
import software.aws.toolkits.jetbrains.core.explorer.devToolsTab.nodes.ActionGroupOnRightClick
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode
import software.aws.toolkits.jetbrains.core.utils.buildList
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager.Companion.ACTION_WHAT_IS_CODEWHISPERER
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.UiTelemetry
import javax.swing.Icon

class CodeWhispererServiceNode(
    project: Project,
    value: String,
) : AbstractActionTreeNode(project, value, null), ActionGroupOnRightClick {
    private val nodeProject
        get() = myProject

    private val whatIsCodeWhispererNode by lazy {
        CodeWhispererActionNode(
            nodeProject,
            message("codewhisperer.explorer.what_is"),
            ACTION_WHAT_IS_CODEWHISPERER,
            0,
            AllIcons.Actions.Help
        )
    }

    override fun onDoubleClick() {}

    override fun getChildren(): Collection<AbstractTreeNode<*>> = buildList {
        add(whatIsCodeWhispererNode)
        add(
            object : AbstractActionTreeNode(
                nodeProject,
                message("codewhisperer.upgrade_ide"),
                AllIcons.General.User
            ) {
                override fun onDoubleClick() {
                    BrowserUtil.browse("https://github.com/aws/aws-toolkit-jetbrains/issues/3684")
                    UiTelemetry.click(project, "cw_upgradeIde_Cta")
                }
            }
        )
    }

    override fun actionGroupName(): String = "aws.toolkit.explorer.codewhisperer"
}

class CodeWhispererActionNode(
    project: Project,
    actionName: String,
    private val actionId: String,
    val order: Int,
    icon: Icon
) : AbstractActionTreeNode(
    project,
    actionName,
    icon
) {
    private val nodeProject
        get() = myProject

    override fun getChildren(): List<AwsExplorerNode<*>> = emptyList()

    override fun onDoubleClick() {
        CodeWhispererExplorerActionManager.getInstance().performAction(nodeProject, actionId)
    }
}
