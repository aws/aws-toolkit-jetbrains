// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.RowFilter
import javax.swing.SortOrder
import javax.swing.event.DocumentEvent

class CloudWatchLogGroup(private val project: Project, private val logGroup: String) {
    val title = logGroup.split("/").last()
    lateinit var content: JPanel

    private lateinit var refreshButton: JButton
    private lateinit var groupsPanel: JBLoadingPanel
    private lateinit var locationInformation: JLabel
    private lateinit var filterField: JBTextField

    private val table: TableView<LogStream>
    private val scrollPane: JScrollPane
    private val client: CloudWatchLogsClient = project.awsClient()

    private fun createUIComponents() {
        groupsPanel = JBLoadingPanel(BorderLayout(), project)
    }

    init {
        table = buildLogGroupTable()
        scrollPane = ScrollPaneFactory.createScrollPane(table)
        locationInformation.text = "${project.activeCredentialProvider().displayName} => ${project.activeRegion().displayName} => $logGroup"
        filterField.emptyText.text = message("cloudwatch.logs.filter_log_streams")
        filterField.document.addDocumentListener(buildStreamSearchListener(table))

        styleRefreshButton()
        groupsPanel.add(scrollPane)

        refreshLogStreams()
    }

    private fun buildLogGroupTable(): TableView<LogStream> {
        val tableView = TableView(
            ListTableModel<LogStream>(
                arrayOf(CloudWatchLogsStreamsColumn(), CloudWatchLogsStreamsColumnDate()),
                listOf<LogStream>(),
                1,
                SortOrder.ASCENDING
            )
        )
        tableView.rowSorter = LogGroupTableSorter(tableView.listTableModel)
        tableView.emptyText.text = message("cloudwatch.logs.no_log_groups")
        tableView.addMouseListener(buildMouseListener(tableView))
        return tableView
    }

    private fun buildStreamSearchListener(table: TableView<*>) = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            val text = filterField.text
            val sorter = (table.rowSorter as LogGroupTableSorter)
            if (text.isNullOrBlank()) {
                sorter.rowFilter = null
            } else {
                sorter.rowFilter = RowFilter.regexFilter(text)
            }
        }
    }

    private fun buildMouseListener(tableView: TableView<LogStream>) = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount < 2 || e.button != MouseEvent.BUTTON1) {
                return
            }
            val row = tableView.rowAtPoint(e.point).takeIf { it >= 0 } ?: return
            val window = CloudWatchLogWindow.getInstance(project)
            GlobalScope.launch {
                window.showLog(logGroup, tableView.getRow(row).logStreamName())
            }
        }
    }

    private fun styleRefreshButton() {
        refreshButton.background = null
        refreshButton.border = null
        refreshButton.icon = AllIcons.Actions.Refresh
        refreshButton.addActionListener { refreshLogStreams() }
    }

    private fun refreshLogStreams() {
        runInEdt {
            groupsPanel.startLoading()
        }
        GlobalScope.launch {
            populateModel()
            runInEdt {
                groupsPanel.stopLoading()
            }
        }
    }

    private suspend fun populateModel() = withContext(Dispatchers.IO) {
        val streams = client.describeLogStreamsPaginator(DescribeLogStreamsRequest.builder().logGroupName(logGroup).build())
        streams.filterNotNull().firstOrNull()?.logStreams()?.let { runInEdt { table.tableViewModel.items = it } }
    }
}
