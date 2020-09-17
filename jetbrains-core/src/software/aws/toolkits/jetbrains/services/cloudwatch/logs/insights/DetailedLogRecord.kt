// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import java.lang.IllegalStateException
import javax.swing.JButton
import javax.swing.JPanel

class DetailedLogRecord(
    private val project: Project,
    private val client: CloudWatchLogsClient,
    private val logRecordPointer: String
) : CoroutineScope by ApplicationThreadPoolScope("DetailedLogEvents"), Disposable {
    val title = message("cloudwatch.logs.log_record", logRecordPointer)
    lateinit var basePanel: JPanel
    lateinit var tableView: TableView<LogRecordFieldPair>
    private lateinit var openStream: JButton
    private var record: LogRecord? = null

    private fun createUIComponents() {
        val model = ListTableModel<LogRecordFieldPair>(
            LogRecordFieldColumn(),
            LogRecordValueColumn()
        )
        tableView = TableView(model).apply {
            setPaintBusy(true)
            emptyText.text = message("loading_resource.loading")
        }
        TableSpeedSearch(tableView)
    }

    init {
        launch {
            val rows = loadLogRecord()
            tableView.listTableModel.items = rows
            openStream.isEnabled = true
        }

        openStream.isEnabled = false
        openStream.addActionListener {
            val logRecord = record ?: throw IllegalStateException("record was not loaded, but tried to open log stream from record")
            val logGroup = logRecord["@log"] ?: throw IllegalStateException("@log was not defined in log record")
            val logStream = logRecord["@logStream"] ?: throw IllegalStateException("@logstream was not defined in log record")

            CloudWatchLogWindow.getInstance(project).showLogStream(
                logGroup = logGroup,
                logStream = logStream
            )
        }
    }

    private fun loadLogRecord(): List<LogRecordFieldPair> {
        val response = client.getLogRecord {
            it.logRecordPointer(logRecordPointer)
        }.logRecord()
        record = response

        return response.map { it.key to it.value }
    }

    override fun dispose() {
    }

    fun getComponent() = basePanel
}
