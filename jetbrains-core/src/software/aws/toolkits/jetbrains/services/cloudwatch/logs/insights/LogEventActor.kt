// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.*
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

sealed class LogEventActor<T>(
    private val project: Project,
    protected val client: CloudWatchLogsClient,
    private val table: TableView<T>
) : CoroutineScope by ApplicationThreadPoolScope("InsightsLogEventResultTable"), Disposable {
    val channel = Channel<MessageLoadEvent>()
    protected abstract val emptyText: String
    protected abstract val tableErrorMessage: String
    protected abstract val notFoundText: String

    private val edtContext = getCoroutineUiContext(disposable = this@LogEventActor)
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        LOG.error(e) { "Exception thrown in Query Results not handled:" }
        notifyError(title = message("general.unknown_error"), project = project)
        channel.close()
    }

    sealed class MessageLoadEvent{
        object LoadLogEventResults : MessageLoadEvent()
    }

    init {
        launch(exceptionHandler) {
            for (message in channel) {
                handleMessages(message)
            }
        }
    }

    private suspend fun handleMessages(message: MessageLoadEvent) {
        loadLogEventResults()
        val rect = table.getCellRect(0, 0, true)
        withContext(edtContext) {
            table.scrollRectToVisible(rect)
        }
    }

    protected suspend fun loadAndPopulateResultsTable(loadBlock: suspend() -> List<T>) {
        try {
            tableLoading()
            val items = loadBlock()
            table.listTableModel.items = items
            table.emptyText.text = emptyText
        } catch (e: ResourceNotFoundException) {
            withContext(edtContext) {
                table.emptyText.text = notFoundText
            }
        } catch (e: Exception) {
            LOG.error(e) { tableErrorMessage }
            withContext(edtContext) {
                table.emptyText.text = tableErrorMessage
                notifyError(title = tableErrorMessage, project = project)
            }
        } finally {
            tableDoneLoading()
        }
    }

    protected open suspend fun loadLogEventResults() {
        throw IllegalStateException("Table does not support loadLogEventResults")
    }

    private suspend fun tableLoading() = withContext(edtContext) {
        table.setPaintBusy(true)
        table.emptyText.text = message("loading_resource.loading")
    }

    private suspend fun tableDoneLoading() = withContext(edtContext) {
        table.tableViewModel.fireTableDataChanged()
        table.setPaintBusy(false)
    }

    override fun dispose() {
        channel.close()
    }
    companion object {
        private val LOG = getLogger<LogEventActor<*>>()
    }
}

class LogEventsResultsActor(
    project: Project,
    client: CloudWatchLogsClient,
    table: TableView<String>,
    private val logEventIdentifier: String
) : LogEventActor<String>(project, client, table) {
    override val emptyText = message("cloudwatch.logs.no_results_found")
    override val tableErrorMessage = message("cloudwatch.logs.query_results_table_error")
    override val notFoundText = message("cloudwatch.logs.no_results_found")

    override suspend fun loadLogEventResults() {
        var request = GetLogRecordRequest.builder().logRecordPointer(logEventIdentifier).build()
        var response = client.getLogRecord(request)
       // val listOfResults= response.logRecord().toMap()
        val listOfResults = response.logRecord().map { "${it.key} = ${it.value}" }
        //val listOfResults = response.logRecord().map { it.key.toString() to it.value.toString() }.toMap()
        loadAndPopulateResultsTable { listOfResults }
    }

}
