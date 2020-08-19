// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.channels.Channel
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.LogActor
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class LogEventTable(
    private val project: Project,
    private val client : CloudWatchLogsClient,
    private val logEventIdentifier: String
): CoroutineScope by ApplicationThreadPoolScope("LogEventTable"), Disposable {
//    val a : JComponent = TODO()
    val component: JComponent
    val channel: Channel<LogEventActor.MessageLoadEvent>
    private val resultsTable: TableView<String>
    private val logEventActor: LogEventActor<String>

    init {
        val tableModel = ListTableModel (arrayOf(LogEventColumnDetails()), mutableListOf<String>())
        resultsTable = TableView(tableModel).apply {
            setPaintBusy(true)
            autoscrolls = true
            emptyText.text = message("loading_resource.loading")
            tableHeader.reorderingAllowed = false
            tableHeader.resizingAllowed = true
        }
        logEventActor = LogEventsResultsActor(project, client, resultsTable, logEventIdentifier)
        channel = logEventActor.channel
        component = ScrollPaneFactory.createScrollPane(resultsTable)
    }

    override fun dispose() {
    }

}

