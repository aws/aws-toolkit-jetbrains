// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.getUserContentLoadLimit
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message

// TODO check resulting file size/ size of log stream and have a pop up if > max editor size
// TODO add progress bar for loading
class OpenLogStreamInEditor(
    private val project: Project,
    private val logGroup: String,
    private val groupTable: JBTable
) : AnAction(message("cloudwatch.logs.open_in_editor"), null, AllIcons.Actions.Menu_open), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val client: CloudWatchLogsClient = project.awsClient()
        val row = groupTable.selectedRow.takeIf { it >= 0 } ?: return
        val logStream = groupTable.getValueAt(row, 0) as String
        ProgressManager.getInstance().run(DownloadTask(project, client, logGroup, logStream))
    }
}

private class DownloadTask(project: Project, val client: CloudWatchLogsClient, val logGroup: String, val logStream: String) :
    Task.Backgroundable(project, message("cloudwatch.logs.opening_in_editor", logStream), true),
    CoroutineScope by ApplicationThreadPoolScope("OpenLogStreamInEditor") {
    private val edt = getCoroutineUiContext(ModalityState.defaultModalityState())

    override fun run(indicator: ProgressIndicator) {
        var events: List<OutputLogEvent>
        var nextToken: String? = null
        val buffer = StringBuilder()
        val maxFileSize = getUserContentLoadLimit()
        do {
            if (buffer.length > maxFileSize) {
                println("way too big yo")
                return
            }
            indicator.checkCanceled()
            val response = client.getLogEvents {
                it
                    .startFromHead(true)
                    .logGroupName(logGroup)
                    .logStreamName(logStream)
                    .nextToken(nextToken)
            }
            events = response.events()
            nextToken = response.nextForwardToken()
            val str = events.joinToString("") { if (it.message().endsWith("\n")) it.message() else "${it.message()}\n" }
            buffer.append(str)
        } while (events.isNotEmpty())
        launch {
            OpenStreamInEditor.open(project, edt, logStream, buffer.toString())
        }
    }

    override fun onCancel() {}
}
