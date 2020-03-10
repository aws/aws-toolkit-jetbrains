// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogsNode
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message

class OpenLogGroup : SingleResourceNodeAction<CloudWatchLogsNode>(message("cloudwatch.logs.open")), DumbAware {
    override fun actionPerformed(selected: CloudWatchLogsNode, e: AnActionEvent) {
        openLogGroup(selected.nodeProject, selected.logGroupName)
    }

    companion object : CoroutineScope by ApplicationThreadPoolScope("openLogGroup") {
        fun openLogGroup(project: Project, logGroupName: String) = launch {
            CloudWatchLogWindow.getInstance(project)?.showLogGroup(logGroupName)
        }
    }
}
