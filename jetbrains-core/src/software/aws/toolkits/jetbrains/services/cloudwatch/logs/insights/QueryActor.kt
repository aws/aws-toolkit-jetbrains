// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.LogActor
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

sealed class QueryActor<T>(
    private val project: Project,
    protected val client: CloudWatchLogsClient,
    private val table: TableView<T>
): CoroutineScope by ApplicationThreadPoolScope("InsightsResultTable"), Disposable {
    val channel = Channel<MessageLoadQueryResults>()
    protected var moreResultsAvailable: Boolean = false
    protected abstract val emptyText: String
    protected abstract val tableErrorMessage: String
    protected abstract val notFoundText: String

    private val edtContext = getCoroutineUiContext(disposable = this@QueryActor)
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        QueryActor.LOG.error(e) { "Exception thrown in Query Results not handled:" }
        notifyError(title = message("general.unknown_error"), project = project)
        table.setPaintBusy(false)
        Disposer.dispose(this)
    }

    sealed class MessageLoadQueryResults{
        object LoadInitialQueryResults : MessageLoadQueryResults()
        object LoadNextQueryBatch :MessageLoadQueryResults()
    }

    init{
        launch (exceptionHandler) {
            messageActionsPerformed()
        }
    }

    private suspend fun messageActionsPerformed(){
        for (message in channel){
            when(message) {
                is MessageLoadQueryResults.LoadNextQueryBatch -> if(moreResultsAvailable) {
                    withContext(edtContext) {table.setPaintBusy(true)}
                    val items = loadNext()
                    withContext(edtContext) {
                        table.listTableModel.addRows(items)
                        table.setPaintBusy(false)
                    }
                }
                is MessageLoadQueryResults.LoadInitialQueryResults -> {
                    loadInitialQueryResults()
                    val rect = table.getCellRect(0, 0, true)
                    withContext(edtContext) {
                        table.scrollRectToVisible(rect)
                    }
                }
            }

        }
    }

    protected suspend fun loadAndPopulateResultsTable (loadBlock: suspend() -> List<T>){
        try{
            tableLoading()
            val items = loadBlock()
            table.listTableModel.items = items
            table.emptyText.text = emptyText
        } catch (e: ResourceNotFoundException) {
            withContext (edtContext) {
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

    protected open suspend fun loadInitialQueryResults() {
        throw IllegalStateException("Table does not support loadInitialQueryResults")
    }

    protected abstract suspend fun loadNext(): List<T>

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
        private val LOG = getLogger<QueryActor<*>>()
    }
}

class QueryResultsActor(
    project: Project,
    client: CloudWatchLogsClient,
    table: TableView<List<ResultField>>,
    private val queryResults : List<List<ResultField>>
): QueryActor<List<ResultField>>(project, client, table){
    override val emptyText = "No Query Results"
    override val tableErrorMessage = "Failed to load Query Results"
    override val notFoundText = "No Query Results Exist"

    override suspend fun loadInitialQueryResults() {
        loadAndPopulateResultsTable { queryResults }
    }

    override suspend fun loadNext(): List<List<ResultField>> {
        return queryResults
    }
}
