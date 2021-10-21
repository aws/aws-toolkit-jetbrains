// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core.tools

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import software.aws.toolkits.jetbrains.services.ssm.SsmPlugin

class SsmTest : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val detectTool = ToolManager.getInstance().getTool(SsmPlugin)
        Messages.showInfoMessage(e.project, detectTool?.path?.toAbsolutePath().toString(), "SSM TEST")

        val detectTool2 = ToolManager.getInstance().getOrInstallTool(SsmPlugin)
        Messages.showInfoMessage(e.project, detectTool2?.path?.toAbsolutePath().toString(), "SSM TEST")
    }
}
