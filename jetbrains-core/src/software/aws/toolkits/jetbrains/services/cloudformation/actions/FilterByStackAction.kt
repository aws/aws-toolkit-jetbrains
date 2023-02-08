// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.core.explorer.filters.AwsExplorerFilterManager
import software.aws.toolkits.jetbrains.core.explorer.filters.CloudFormationStackFilter
import software.aws.toolkits.jetbrains.services.cloudformation.CloudFormationStackNode
import software.aws.toolkits.resources.message

class FilterByStackAction :
    SingleResourceNodeAction<CloudFormationStackNode>(message("cloudformation.filter.action"), null, AllIcons.General.Filter),
    DumbAware {
    override fun actionPerformed(selected: CloudFormationStackNode, e: AnActionEvent) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val filter = CloudFormationStackFilter.newInstance(selected.nodeProject, selected.stackName)
            AwsExplorerFilterManager.getInstance(selected.nodeProject).setFilter(filter)
        }
    }
}
