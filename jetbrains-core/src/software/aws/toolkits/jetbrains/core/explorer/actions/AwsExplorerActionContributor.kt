// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.actions

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.ExtensionPointName
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerNode

/**
 * Primarily used to add actions to explorer nodes.
 *
 * Can also be used to do other processing of the action group, e.g. ordering etc
 */
interface AwsExplorerActionContributor {
    companion object {
        val EP_NAME = ExtensionPointName<AwsExplorerActionContributor>("aws.toolkit.explorer.actionContributor")
    }

    /**
     * Add (or mutate) the action [group] for the specified [node]
     */
    fun process(group: DefaultActionGroup, node: AwsExplorerNode<*>)
}
