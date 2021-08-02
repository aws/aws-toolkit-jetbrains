// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindow
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.CloudWatchLogGroup
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor.CloudWatchLogStream
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.DetailedLogRecord
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.QueryDetails
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights.QueryResultList
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CloudwatchlogsTelemetry
import software.aws.toolkits.telemetry.Result
import java.time.Duration

class CloudWatchLogWindow(override val project: Project) : ToolkitToolWindow {
    override val toolWindowId = "aws.cloudwatchlogs"
    private val edtContext = getCoroutineUiContext()

    suspend fun showLogGroup(logGroup: String) {
        var result = Result.Succeeded
        try {
            if (showExistingContent(logGroup)) {
                return
            }
            val group = CloudWatchLogGroup(project, logGroup)
            val title = message("cloudwatch.logs.log_group_title", logGroup.split("/").last())
            withContext(edtContext) {
                addTab(title, group.content, activate = true, id = logGroup)
            }
        } catch (e: Exception) {
            LOG.error(e) { "Exception thrown while trying to show log group '$logGroup'" }
            result = Result.Failed
            throw e
        } finally {
            CloudwatchlogsTelemetry.openGroup(project, result)
        }
    }

    suspend fun showLogStream(
        logGroup: String,
        logStream: String,
        previousEvent: LogStreamEntry? = null,
        duration: Duration? = null,
        streamLogs: Boolean = false
    ) {
        var result = Result.Succeeded
        try {
            val id = "$logGroup/$logStream/${previousEvent?.timestamp}/${previousEvent?.message}/$duration"
            if (showExistingContent(id)) {
                return
            }
            val title = if (previousEvent != null && duration != null) {
                message(
                    "cloudwatch.logs.filtered_log_stream_title",
                    logStream,
                    DateFormatUtil.getDateTimeFormat().format(previousEvent.timestamp - duration.toMillis()),
                    DateFormatUtil.getDateTimeFormat().format(previousEvent.timestamp + duration.toMillis())
                )
            } else {
                message("cloudwatch.logs.log_stream_title", logStream)
            }
            val stream = CloudWatchLogStream(project, logGroup, logStream, previousEvent, duration, streamLogs)
            withContext(edtContext) {
                addTab(title, stream.content, activate = true, id = id)
            }
        } catch (e: Exception) {
            LOG.error(e) { "Exception thrown while trying to show log group '$logGroup' stream '$logStream'" }
            result = Result.Failed
            throw e
        } finally {
            CloudwatchlogsTelemetry.openStream(project, result)
        }
    }

    suspend fun showQueryResults(queryDetails: QueryDetails, queryId: String, fields: List<String>) {
        if (showExistingContent(queryId)) {
            return
        }

        val queryResult = QueryResultList(project, fields, queryId, queryDetails)
        val title = message("cloudwatch.logs.query_tab_title", queryId)
        withContext(edtContext) {
            addTab(title, queryResult.resultsPanel, activate = true, id = queryId)
        }
    }

    suspend fun showDetailedEvent(client: CloudWatchLogsClient, identifier: String) {
        if (showExistingContent(identifier)) {
            return
        }

        val detailedLogEvent = DetailedLogRecord(project, client, identifier)
        withContext(edtContext) {
            addTab(detailedLogEvent.title, detailedLogEvent.getComponent(), activate = true, id = identifier)
        }
    }

    fun closeLogGroup(logGroup: String) {
        findPrefix(logGroup).forEach {
            removeContent(it)
        }
    }

    companion object {
        fun getInstance(project: Project) = ServiceManager.getService(project, CloudWatchLogWindow::class.java)
        private val LOG = getLogger<CloudWatchLogWindow>()
    }
}
