// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.table.JBTable
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.LogStreamDownloadTask
import software.aws.toolkits.resources.message

class OpenLogStreamInEditor(
    private val project: Project,
    private val client: CloudWatchLogsClient,
    private val logGroup: String,
    private val groupTable: JBTable
) : AnAction(message("cloudwatch.logs.open_in_editor"), null, AllIcons.Actions.Menu_open), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val row = groupTable.selectedRow.takeIf { it >= 0 } ?: return
        val logStream = groupTable.getValueAt(row, 0) as String
        ProgressManager.getInstance().run(LogStreamDownloadTask(project, client, logGroup, logStream))
    }
}

