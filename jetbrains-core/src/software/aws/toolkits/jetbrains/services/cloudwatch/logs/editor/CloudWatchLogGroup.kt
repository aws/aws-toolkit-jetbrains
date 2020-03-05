package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
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
import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class CloudWatchLogGroup(private val project: Project, private val cloudWatchLogsClient: CloudWatchLogsClient, private val logGroup: String) {
    val title = logGroup.split("/").last()
    lateinit var content: JPanel
    lateinit var refreshButton: JButton
    lateinit var groupsPanel: JBLoadingPanel
    lateinit var locationInformation: JLabel
    lateinit var filterField: JBTextField

    private val table: TableView<LogStream> = TableView(
        ListTableModel<LogStream>(
            arrayOf(CloudWatchLogsStreamsColumn(), CloudWatchLogsStreamsColumnDate()),
            listOf<LogStream>(),
            1,
            SortOrder.ASCENDING
        )
    )
    private val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(table)
    private val doubleClickListener = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount < 2 || e.button != MouseEvent.BUTTON1) {
                return
            }
            val row = table.rowAtPoint(e.point).takeIf { it >= 0 } ?: return
            val window = CloudWatchLogWindow.getInstance(project)
            GlobalScope.launch {
                window.showLog(logGroup, table.getRow(row).logStreamName())
            }
        }
    }

    private fun createUIComponents() {
        groupsPanel = JBLoadingPanel(BorderLayout(), project)
    }

    init {
        table.rowSorter = object : TableRowSorter<ListTableModel<LogStream>>(table.listTableModel) {
            init {
                sortKeys = listOf(SortKey(1, SortOrder.DESCENDING))
                setSortable(0, false)
                setSortable(1, false)
            }
        }
        table.addMouseListener(doubleClickListener)
        locationInformation.text = "${project.activeCredentialProvider().displayName} => ${project.activeRegion().displayName} => $logGroup"
        filterField.emptyText.text = message("cloudwatch.logs.filter_log_streams")

        refreshButton.isBorderPainted = false
        refreshButton.isContentAreaFilled = false
        refreshButton.icon = AllIcons.Actions.Refresh
        refreshButton.addActionListener { refresh() }

        groupsPanel.add(scrollPane)

        refresh()
    }


    private fun refresh() {
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
        val streams = cloudWatchLogsClient.describeLogStreamsPaginator(DescribeLogStreamsRequest.builder().logGroupName(logGroup).build())
        streams.filterNotNull().firstOrNull()?.logStreams()?.let { runInEdt { table.tableViewModel.items = it } }
    }
}
