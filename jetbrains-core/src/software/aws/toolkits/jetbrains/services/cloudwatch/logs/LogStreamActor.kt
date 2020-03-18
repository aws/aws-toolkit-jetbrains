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
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import java.time.Duration

sealed class LogStreamActor(
    private val project: Project,
    private val table: TableView<LogStreamEntry>,
    protected val logGroup: String,
    protected val logStream: String
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsStream"), Disposable {
    val channel = Channel<Messages>()
    protected val client: CloudWatchLogsAsyncClient = project.awsClient()
    private val edtContext = getCoroutineUiContext(disposable = this)

    protected var nextBackwardToken: String? = null
    protected var nextForwardToken: String? = null

    sealed class Messages {
        class LOAD_INITIAL : Messages()
        class LOAD_INITIAL_RANGE(val startTime: Long, val duration: Duration) : Messages()
        class LOAD_INITIAL_FILTER(val queryString: String) : Messages()
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
                    val items = loadMore(nextForwardToken, saveForwardToken = true)
                    withContext(edtContext) { table.listTableModel.addRows(items) }
                }
                is Messages.LOAD_BACKWARD -> {
                    val items = loadMore(nextBackwardToken, saveBackwardToken = true)
                    withContext(edtContext) { table.listTableModel.items = items + table.listTableModel.items }
                }
                is Messages.LOAD_INITIAL -> {
                    loadInitial()
                }
                is Messages.LOAD_INITIAL_RANGE -> {
                    loadInitialRange(message.startTime, message.duration)
                }
                is Messages.LOAD_INITIAL_FILTER -> {
                    loadInitialFilter(message.queryString)
                }
            }
        }
    }

    protected suspend fun loadAndPopulate(loadBlock: suspend () -> List<LogStreamEntry>) {
        try {
            val items = loadBlock()
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

    protected abstract suspend fun loadInitial()
    protected abstract suspend fun loadInitialRange(startTime: Long, duration: Duration)
    protected abstract suspend fun loadInitialFilter(queryString: String)
    protected abstract suspend fun loadMore(nextToken: String?, saveForwardToken: Boolean = false, saveBackwardToken: Boolean = false): List<LogStreamEntry>

    override fun dispose() {
        channel.close()
        cancel()
    }

    companion object {
        private val LOG = getLogger<LogStreamActor>()
    }
}

class LogStreamFilterActor(
    project: Project,
    table: TableView<LogStreamEntry>,
    logGroup: String,
    logStream: String
) : LogStreamActor(project, table, logGroup, logStream) {
    override suspend fun loadInitial() {
        throw IllegalStateException("FilterActor can only loadInitialFilter")
    }

    override suspend fun loadInitialFilter(queryString: String) {
        val request = FilterLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamNames(logStream)
            .filterPattern(queryString)
            .build()
        loadAndPopulate { getSearchLogEvents(request) }
    }

    override suspend fun loadInitialRange(startTime: Long, duration: Duration) {
        throw IllegalStateException("FilterActor can only loadInitialFilter")
    }

    override suspend fun loadMore(nextToken: String?, saveForwardToken: Boolean, saveBackwardToken: Boolean): List<LogStreamEntry> {
        val request = FilterLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamNames(logStream)
            .nextToken(nextToken)
            .build()

        return getSearchLogEvents(request)
    }

    private suspend fun getSearchLogEvents(request: FilterLogEventsRequest): List<LogStreamEntry> {
        val response = client.filterLogEvents(request).asDeferred().await()
        val events = response.events().filterNotNull().map { it.toLogStreamEntry() }
        nextForwardToken = response.nextToken()

        return events
    }
}

class LogStreamListActor(
    project: Project,
    table: TableView<LogStreamEntry>,
    logGroup: String,
    logStream: String
) :
    LogStreamActor(project, table, logGroup, logStream) {
    override suspend fun loadInitial() {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .build()
        loadAndPopulate { getLogEvents(request, saveForwardToken = true, saveBackwardToken = true) }
    }

    override suspend fun loadInitialRange(startTime: Long, duration: Duration) {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .startTime(startTime - duration.toMillis())
            .endTime(startTime + duration.toMillis())
            .build()
        loadAndPopulate { getLogEvents(request, saveForwardToken = true, saveBackwardToken = true) }
    }

    override suspend fun loadInitialFilter(queryString: String) {
        throw IllegalStateException("GetActor can not loadInitialFilter")
    }

    override suspend fun loadMore(nextToken: String?, saveForwardToken: Boolean, saveBackwardToken: Boolean): List<LogStreamEntry> {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .nextToken(nextToken)
            .build()

        return getLogEvents(request, saveForwardToken = saveForwardToken, saveBackwardToken = saveBackwardToken)
    }

    private suspend fun getLogEvents(
        request: GetLogEventsRequest,
        saveForwardToken: Boolean = false,
        saveBackwardToken: Boolean = false
    ): List<LogStreamEntry> {
        val response = client.getLogEvents(request).asDeferred().await()
        val events = response.events().filterNotNull().map { it.toLogStreamEntry() }
        if (saveForwardToken) {
            nextForwardToken = response.nextForwardToken()
        }
        if (saveBackwardToken) {
            nextBackwardToken = response.nextBackwardToken()
        }

        return events
    }
}
