// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StacksNode
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager

class CloudFormationRootNode(private val nodeProject: Project) : AbstractTreeNode<Any>(nodeProject, Any()) {
    override fun update(presentation: PresentationData) {}

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val stacksManager = StacksManager.getInstance(nodeProject)
        val changeSetsManager = ChangeSetsManager.getInstance(nodeProject)

        return listOf(
            StacksNode(nodeProject, stacksManager, changeSetsManager)
        )
    }
}
