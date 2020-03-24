// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import java.io.File
import java.time.Instant

class DownloadLogStream(
    private val project: Project,
    private val client: CloudWatchLogsClient,
    private val logGroup: String,
    private val logStream: String
) : AnAction(message("cloudwatch.logs.save_action"), null, AllIcons.Actions.Menu_saveall), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        ProgressManager.getInstance().run(DownloadLog(project, client, logGroup, logStream, ""))
    }
}

class DownloadLog(
    project: Project,
    private val client: CloudWatchLogsClient,
    private val logGroup: String,
    private val logStream: String,
    private val buffer: String
) : Task.Backgroundable(project, message("cloudwatch.logs.saving_to_disk", logStream), true) {
    private val edt = getCoroutineUiContext()

    override fun run(indicator: ProgressIndicator) {
        runBlocking {
            val startTime = Instant.now()
            val request = GetLogEventsRequest
                .builder()
                .startFromHead(true)
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .endTime(startTime.toEpochMilli())
                .build()
            promptToDownload(indicator, request, buffer)
        }
    }

    suspend fun promptToDownload(indicator: ProgressIndicator, request: GetLogEventsRequest, buffer: String) {
        val descriptor = FileSaverDescriptor(message("s3.download.object.action"), message("s3.download.object.description"))
        val saveLocation = withContext(edt) {
            val destination = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            destination.save(null, null)
        }
        if (saveLocation != null) {
            streamLogStreamToFile(indicator, request, saveLocation.file, buffer)
        }
    }

    private fun streamLogStreamToFile(indicator: ProgressIndicator, request: GetLogEventsRequest, file: File, buffer: String) {
        try {
            file.appendText(buffer)
            val getRequest = client.getLogEventsPaginator(request)
            getRequest.stream().forEach {
                indicator.checkCanceled()
                val str = it.events().buildStringFromLogsOutput()
                file.appendText(str)
            }
            notifyInfo(
                project = project,
                title = message("aws.notification.title"),
                content = message("cloudwatch.logs.saving_to_disk_succeeded", logStream)
            )
        } catch (e: Exception) {
            LOG.error(e) { "Exception thrown while downloading large log stream" }
            e.notifyError(project = project, title = message("cloudwatch.logs.saving_to_disk_failed", logStream))
        }
    }

    companion object {
        val LOG = getLogger<DownloadLogStream>()
    }
}
