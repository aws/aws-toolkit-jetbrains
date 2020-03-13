// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsAsyncClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message

class CloudWatchLogStreamCoroutine(
    project: Project,
    private val table: JBTable,
    private val logGroup: String,
    private val logStream: String
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsStream"), Disposable {
    val channel = Channel<Messages>()
    private val edtContext = getCoroutineUiContext(disposable = this)

    private val client: CloudWatchLogsAsyncClient = project.awsClient()
    private var nextBackwardToken: String? = null
    private var nextForwardToken: String? = null

    enum class Messages {
        LOAD_FORWARD,
        LOAD_BACKWARD
    }

    suspend fun loadInitial() {
        val request = GetLogEventsRequest.builder().logGroupName(logGroup).logStreamName(logStream).startFromHead(true).build()
        val items = load(request, saveForwardToken = true, saveBackwardToken = true)
        populateTable(items)
        startListening()
    }

    suspend fun loadInitialAround(startTime: Long, duration: Long) {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .startTime(startTime - duration)
            .endTime(startTime + duration)
            .build()
        val items = load(request, saveForwardToken = true, saveBackwardToken = true)
        populateTable(items)
        startListening()
    }

    private suspend fun populateTable(items: List<OutputLogEvent>) {
        if (items.isNotEmpty()) {
            val events = table.logsModel.items.plus(items)
            withContext(edtContext) { table.logsModel.items = events }
        }
        table.emptyText.text = message("cloudwatch.logs.no_events")
        table.setPaintBusy(false)
    }

    private suspend fun startListening() = coroutineScope {
        launch {
            for (message in channel) {
                when (message) {
                    Messages.LOAD_FORWARD -> {
                        val items = loadMore(nextForwardToken, saveForwardToken = true)
                        if (items.isNotEmpty()) {
                            val events = table.logsModel.items.plus(items)
                            withContext(edtContext) { table.logsModel.items = events }
                        }
                    }
                    Messages.LOAD_BACKWARD -> {
                        val items = loadMore(nextBackwardToken, saveBackwardToken = true)
                        if (items.isNotEmpty()) {
                            val events = items.plus(table.logsModel.items)
                            withContext(edtContext) { table.logsModel.items = events }
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadMore(
        nextToken: String?,
        saveForwardToken: Boolean = false,
        saveBackwardToken: Boolean = false
    ): List<OutputLogEvent> {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .nextToken(nextToken)
            .build()

        return load(request, saveForwardToken = saveForwardToken, saveBackwardToken = saveBackwardToken)
    }

    private suspend fun load(
        request: GetLogEventsRequest,
        saveForwardToken: Boolean = false,
        saveBackwardToken: Boolean = false
    ): List<OutputLogEvent> {
        val response = client.getLogEvents(request).asDeferred().await()
        val events = response.events().filterNotNull()
        if (saveForwardToken) {
            nextForwardToken = response.nextForwardToken()
        }
        if (saveBackwardToken) {
            nextBackwardToken = response.nextBackwardToken()
        }

        return events
    }

    override fun dispose() {
        channel.close()
    }

    private val JBTable.logsModel: ListTableModel<OutputLogEvent> get() = this.model as ListTableModel<OutputLogEvent>
}
