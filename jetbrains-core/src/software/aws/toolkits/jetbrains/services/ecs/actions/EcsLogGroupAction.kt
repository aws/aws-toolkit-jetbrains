// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.ecs.EcsServiceNode
import software.aws.toolkits.resources.message

class EcsLogGroupAction : SingleResourceNodeAction<EcsServiceNode>(message("cloudwatch.logs.show_log_group")) {
    override fun actionPerformed(selected: EcsServiceNode, e: AnActionEvent) {
        
    }
}
