// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.explorer.nodes.AwsExplorerResourceNode

class ViewResourceAction<in T : AwsExplorerResourceNode<*>>(text: String) :
    SingleResourceNodeAction<T>(text, icon = AllIcons.Actions.Cancel), DumbAware {
    override fun actionPerformed(selected: T, e: AnActionEvent) {
        TODO("Not yet implemented")
    }
}
