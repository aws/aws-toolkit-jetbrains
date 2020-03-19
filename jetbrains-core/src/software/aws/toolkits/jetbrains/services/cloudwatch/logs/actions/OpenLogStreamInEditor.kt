// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtilRt.getUserContentLoadLimit
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message
import java.io.File
import java.time.Instant

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
        runBlocking {
            runSuspend(indicator)
        }
    }

    suspend fun runSuspend(indicator: ProgressIndicator) {
        val startTime = Instant.now()
        var events: List<OutputLogEvent>
        var nextToken: String? = null
        val buffer = StringBuilder()
        // Default content load limit is 20MB, but utf-8 strings (the default encoding for intellij files) are
        // variable length, so we divide by 2 which should work for all normal logs. Sadly we do not get back
        // what the string is encoded from the service so we can't just
        val maxBufferLength = getUserContentLoadLimit() / 2
        do {
            if (buffer.length > maxBufferLength) {
                handleLargeLogStream(indicator, startTime, nextToken, buffer)
            }
            indicator.checkCanceled()
            val response = client.getLogEvents {
                it
                    .startFromHead(true)
                    .logGroupName(logGroup)
                    .logStreamName(logStream)
                    .endTime(startTime.toEpochMilli())
                    .nextToken(nextToken)
            }
            events = response.events()
            nextToken = response.nextForwardToken()
            val str = events.buildStringFromLogs()
            buffer.append(str)
        } while (events.isNotEmpty())

        OpenStreamInEditor.open(project, edt, logStream, buffer.toString())
    }

    private suspend fun handleLargeLogStream(indicator: ProgressIndicator, startTime: Instant, nextToken: String?, buffer: StringBuilder) {
        when (promptWriteToFile()) {
            Messages.OK -> {
                val descriptor = FileSaverDescriptor(message("s3.download.object.action"), message("s3.download.object.description"))
                val saveLocation = withContext(edt) {
                    val destination = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
                    destination.save(null, null)
                }
                if (saveLocation != null) {
                    streamLogStreamToFile(indicator, startTime, nextToken, saveLocation.file, buffer)
                }
            }
        }
        indicator.cancel()
    }

    private suspend fun promptWriteToFile(): Int = withContext(edt) {
        return@withContext Messages.showOkCancelDialog(
            project,
            message("cloudwatch.logs.stream_too_big_message", logStream),
            message("cloudwatch.logs.stream_too_big"),
            message("cloudwatch.logs.stream_save_to_file", logStream),
            Messages.CANCEL_BUTTON,
            AllIcons.General.QuestionDialog
        )
    }

    private fun streamLogStreamToFile(indicator: ProgressIndicator, startTime: Instant, next: String?, file: File, buffer: StringBuilder) {
        file.appendText(buffer.toString())
        var events: List<OutputLogEvent>
        var nextToken: String? = next
        do {
            indicator.checkCanceled()
            val response = client.getLogEvents {
                it
                    .startFromHead(true)
                    .logGroupName(logGroup)
                    .logStreamName(logStream)
                    .endTime(startTime.toEpochMilli())
                    .nextToken(nextToken)
            }
            events = response.events()
            nextToken = response.nextForwardToken()
            val str = events.buildStringFromLogs()
            file.appendText(str)
        } while (events.isNotEmpty())
    }
}
