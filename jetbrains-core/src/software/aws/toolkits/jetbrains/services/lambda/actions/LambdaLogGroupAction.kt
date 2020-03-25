// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.core.explorer.actions.ExplorerNodeAction
import software.aws.toolkits.jetbrains.services.lambda.LambdaFunctionNode
import software.aws.toolkits.resources.message

class LambdaLogGroupAction : ExplorerNodeAction<LambdaFunctionNode>(message("lambda.logs.action_label")) {
    override fun actionPerformed(selected: List<LambdaFunctionNode>, e: AnActionEvent) {

    }
}
