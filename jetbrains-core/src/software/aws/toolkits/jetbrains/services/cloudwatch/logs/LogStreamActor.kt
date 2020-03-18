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

class LogStreamActor(
    private val project: Project,
    private val table: TableView<LogStreamEntry>,
    private val logGroup: String,
    private val logStream: String
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsStream"), Disposable {
    val channel = Channel<Messages>()
    private val client: CloudWatchLogsAsyncClient = project.awsClient()
    private val edtContext = getCoroutineUiContext(disposable = this)

    private var clientType: ClientType? = null
    private var nextBackwardToken: String? = null
    private var nextForwardToken: String? = null

    sealed class Messages {
        class LOAD_INITIAL : Messages()
        class LOAD_INITIAL_RANGE(val startTime: Long, val duration: Duration) : Messages()
        class LOAD_INITIAL_SEARCH(val queryString: String) : Messages()
        class LOAD_FORWARD : Messages()
        class LOAD_BACKWARD : Messages()
    }

    private enum class ClientType {
        GET,
        FILTER
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
                    if (nextForwardToken != null) {
                        val items = if (clientType == ClientType.GET) {
                            loadMore(nextForwardToken, saveForwardToken = true)
                        } else {
                            loadMoreSearch(nextForwardToken)
                        }
                        withContext(edtContext) { table.listTableModel.addRows(items) }
                    }
                }
                is Messages.LOAD_BACKWARD -> {
                    if (nextBackwardToken != null) {
                        val items = loadMore(nextBackwardToken, saveBackwardToken = true)
                        withContext(edtContext) { table.listTableModel.items = items + table.listTableModel.items }
                    }
                }
                is Messages.LOAD_INITIAL -> {
                    // skip loading if we have a client
                    if (clientType == null) {
                        clientType = ClientType.GET
                        loadInitial()
                    }
                }
                is Messages.LOAD_INITIAL_RANGE -> {
                    // skip loading if we have a client
                    if (clientType == null) {
                        clientType = ClientType.GET
                        loadInitialRange(message.startTime, message.duration)
                    }
                }
                is Messages.LOAD_INITIAL_SEARCH -> {
                    // skip loading if we have a client
                    if (clientType == null) {
                        clientType = ClientType.FILTER
                        loadInitialSearch(message.queryString)
                    }
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
        loadAndPopulate { getLogEvents(request, saveForwardToken = true, saveBackwardToken = true) }
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
        loadAndPopulate { getLogEvents(request, saveForwardToken = true, saveBackwardToken = true) }
    }

    private suspend fun loadInitialSearch(queryString: String) {
        val request = FilterLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamNames(logStream)
            .filterPattern(queryString)
            .build()
        loadAndPopulate { getSearchLogEvents(request) }
    }

    private suspend fun loadAndPopulate(loadBlock: suspend () -> List<LogStreamEntry>) {
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

    private suspend fun loadMore(
        nextToken: String?,
        saveForwardToken: Boolean = false,
        saveBackwardToken: Boolean = false
    ): List<LogStreamEntry> {
        val request = GetLogEventsRequest
            .builder()
            .logGroupName(logGroup)
            .logStreamName(logStream)
            .startFromHead(true)
            .nextToken(nextToken)
            .build()

        return getLogEvents(request, saveForwardToken = saveForwardToken, saveBackwardToken = saveBackwardToken)
    }

    private suspend fun loadMoreSearch(nextToken: String?): List<LogStreamEntry> {
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

    override fun dispose() {
        channel.close()
        cancel()
    }

    companion object {
        private val LOG = getLogger<LogStreamActor>()
    }
}
