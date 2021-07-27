// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import icons.AwsIcons
import software.aws.toolkits.jetbrains.core.toolwindow.AbstractToolkitToolWindow
import software.aws.toolkits.resources.message

class CloudWatchLogsToolWindow : AbstractToolkitToolWindow() {
    companion object {
        const val TOOLWINDOW_ID = "aws.cloudwatchlogs"
        private val task = RegisterToolWindowTask(
            id = TOOLWINDOW_ID,
            anchor = ToolWindowAnchor.BOTTOM,
            canCloseContent = true,
            icon = AwsIcons.Resources.CloudWatch.LOGS_TOOL_WINDOW,
            stripeTitle = { message("cloudwatch.logs.toolwindow") }
        )

        fun getOrCreateToolWindow(project: Project) = getOrCreateToolWindow(project, task)
    }
}
