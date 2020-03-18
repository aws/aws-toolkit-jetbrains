// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsAsyncClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import java.time.Duration

class LogStreamActor(
    private val project: Project,
    private val table: TableView<LogStreamEntry>,
    private val logGroup: String,
    private val logStream: String
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsStream"), Disposable {
    val channel = Channel<Messages>()
    private val client: CloudWatchLogsAsyncClient = project.awsClient()
    private val edtContext = getCoroutineUiContext(disposable = this)

    private var nextBackwardToken: String? = null
    private var nextForwardToken: String? = null

    sealed class Messages {
        class LOAD_INITIAL : Messages()
        class LOAD_INITIAL_RANGE(val startTime: Long, val duration: Duration) : Messages()
        class LOAD_INITIAL_SEARCH(val queryString: String) : Messages()
        class LOAD_FORWARD : Messages()
        class LOAD_BACKWARD : Messages()
    }

    init {
        launch {
            startListening()
        }
    }

    private suspend fun startListening() {
        for (message in channel) {
            when (message) {
                is Messages.LOAD_FORWARD -> {
                    val items = loadMore(nextForwardToken, saveForwardToken = true).map { it.toLogStreamEntry() }
                    withContext(edtContext) { table.listTableModel.addRows(items) }
                }
                is Messages.LOAD_BACKWARD -> {
                    val items = loadMore(nextBackwardToken, saveBackwardToken = true).map { it.toLogStreamEntry() }
                    withContext(edtContext) { table.listTableModel.items = items + table.listTableModel.items }
                }
                is Messages.LOAD_INITIAL -> {
                    loadInitial()
                }
                is Messages.LOAD_INITIAL_RANGE -> {
                    loadInitialRange(message.startTime, message.duration)
                }
                is Messages.LOAD_INITIAL_SEARCH -> {
                    TODO("TODO")
                }
            }
        }
    }

    private suspend fun loadInitial() {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .build()
        loadAndPopulate(request)
    }

    private suspend fun loadInitialRange(startTime: Long, duration: Duration) {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .startTime(startTime - duration.toMillis())
            .endTime(startTime + duration.toMillis())
            .build()
        loadAndPopulate(request)
    }


    private suspend fun loadAndPopulate(request: GetLogEventsRequest) {
        try {
            val items = load(request, saveForwardToken = true, saveBackwardToken = true).map { it.toLogStreamEntry() }
            withContext(edtContext) {
                table.listTableModel.addRows(items)
            }
        } catch (e: Exception) {
            val errorMessage = message("cloudwatch.logs.failed_to_load_stream", logStream)
            LOG.error(e) { errorMessage }
            notifyError(title = errorMessage, project = project)
            withContext(edtContext) { table.emptyText.text = errorMessage }
        } finally {
            withContext(edtContext) {
                table.emptyText.text = message("cloudwatch.logs.no_events")
                table.setPaintBusy(false)
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
        cancel()
    }

    companion object {
        private val LOG = getLogger<LogStreamActor>()
    }
}
