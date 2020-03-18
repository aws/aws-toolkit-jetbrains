// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.LogStreamActor
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SortOrder

class LogStreamTable(
    val project: Project,
    val logGroup: String,
    val logStream: String,
    startTime: Long? = null,
    duration: Long? = null
) :
    CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsGroup"), Disposable {

    val component: JScrollPane
    val channel: Channel<LogStreamActor.Messages>
    val logsTable: TableView<OutputLogEvent>
    private val logStreamActor: LogStreamActor
    private var logStreamingJob: Deferred<*>? = null
    private val edtContext = getCoroutineUiContext(disposable = this)

    init {
        val model = ListTableModel<OutputLogEvent>(
            arrayOf(LogStreamDateColumn(), LogStreamMessageColumn()),
            mutableListOf<OutputLogEvent>(),
            // Don't sort in the model because the requests come sorted
            -1,
            SortOrder.UNSORTED
        )
        logsTable = TableView(model).apply {
            setPaintBusy(true)
            autoscrolls = true
            emptyText.text = message("loading_resource.loading")
            tableHeader.reorderingAllowed = false
        }
        // TODO fix resizing
        logsTable.columnModel.getColumn(0).preferredWidth = 150
        logsTable.columnModel.getColumn(0).maxWidth = 150
        logsTable.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN

        component = ScrollPaneFactory.createScrollPane(logsTable)

        logStreamActor = LogStreamActor(project.awsClient(), logsTable, logGroup, logStream)
        channel = logStreamActor.channel
        Disposer.register(this, logStreamActor)

        component.verticalScrollBar.addAdjustmentListener {
            if (logsTable.model.rowCount == 0) {
                return@addAdjustmentListener
            }
            if (component.verticalScrollBar.isAtBottom()) {
                launch {
                    // Don't load more if there is a logStreamingJob because then it will just keep loading forever at the bottom
                    if (logStreamingJob == null) {
                        logStreamActor.channel.send(LogStreamActor.Messages.LOAD_FORWARD)
                    }
                }
            } else if (component.verticalScrollBar.isAtTop()) {
                launch { logStreamActor.channel.send(LogStreamActor.Messages.LOAD_BACKWARD) }
            }
        }

        launch {
            try {
                if (startTime != null && duration != null) {
                    logStreamActor.loadInitialRange(startTime, duration)
                } else {
                    logStreamActor.loadInitial()
                }
                logStreamActor.startListening()
            } catch (e: Exception) {
                val errorMessage = message("cloudwatch.logs.failed_to_load_stream", logStream)
                LOG.error(e) { errorMessage }
                notifyError(title = errorMessage, project = project)
                withContext(edtContext) { logsTable.emptyText.text = errorMessage }
            }
        }
    }

    private fun JScrollBar.isAtBottom(): Boolean = value == (maximum - visibleAmount)
    private fun JScrollBar.isAtTop(): Boolean = value == minimum

    override fun dispose() {}

    companion object {
        private val LOG = getLogger<LogStreamTable>()
    }
}
