// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

class RefreshStacksAction : AnAction(message("cloudformation.explorer.refresh")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        StacksManager.getInstance(project).reload()
    }
}
