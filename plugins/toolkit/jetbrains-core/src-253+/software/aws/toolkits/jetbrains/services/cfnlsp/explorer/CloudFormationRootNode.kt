// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.ResourcesNode
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StacksNode
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceLoader
import software.aws.toolkits.jetbrains.services.cfnlsp.resources.ResourceTypesManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager

class CloudFormationRootNode(private val nodeProject: Project) : AbstractTreeNode<Any>(nodeProject, Any()) {
    private val stacksManager by lazy { StacksManager.getInstance(nodeProject) }
    private val changeSetsManager by lazy { ChangeSetsManager.getInstance(nodeProject) }
    private val resourceTypesManager by lazy { ResourceTypesManager.getInstance(nodeProject) }
    private val resourceLoader by lazy { ResourceLoader.getInstance(nodeProject) }

    override fun update(presentation: PresentationData) {}

    override fun getChildren(): Collection<AbstractTreeNode<*>> = listOf(
        StacksNode(nodeProject, stacksManager, changeSetsManager),
        ResourcesNode(nodeProject, resourceTypesManager, resourceLoader)
    )
}
