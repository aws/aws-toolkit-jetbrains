// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.nodes

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import software.aws.toolkits.jetbrains.core.explorer.actions.AwsExplorerActionContributor

/**
 * Implemented by explorer resources that have associated actions.
 *
 * @see AwsExplorerServiceRootNode for actions associated with top-level service nodes
 * @see AwsExplorerResourceNode for actions associated with an ARN-aware AWS resource
 */
interface ResourceActionNode {
    /**
     * @returns The name of the action group declared in plugin.xml that is associated with this explorer node
     */
    fun actionGroupName(): String
}

class ResourceActionNodeContributor : AwsExplorerActionContributor {
    private val actionManager = ActionManager.getInstance()

    override fun process(group: DefaultActionGroup, node: AwsExplorerNode<*>) {
        if (node !is ResourceActionNode) return
        (actionManager.getAction(node.actionGroupName()) as? ActionGroup)?.let { group.addAll(it) }
    }
}
