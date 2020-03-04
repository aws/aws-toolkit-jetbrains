package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class CloudWatchLogGroup(private val project: Project, private val cloudWatchLogsClient: CloudWatchLogsClient, private val logGroup: String) {
    val title = logGroup.split("/").last()
    lateinit var content: JPanel
    lateinit var groupsPanel: JPanel
    lateinit var textInformation: JLabel
    lateinit var filterField: JTextField

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

    init {
        table.rowSorter = object : TableRowSorter<ListTableModel<LogStream>>(table.listTableModel) {
            init {
                sortKeys = listOf(SortKey(1, SortOrder.DESCENDING))
                setSortable(0, false)
                setSortable(1, false)
            }
        }
        groupsPanel.add(scrollPane)
        table.addMouseListener(doubleClickListener)
        textInformation.text = "${project.activeRegion().displayName} => $logGroup"

        GlobalScope.launch {
            populateModel()
        }
    }

    private suspend fun populateModel() = withContext(Dispatchers.IO) {
        val streams = cloudWatchLogsClient.describeLogStreamsPaginator(DescribeLogStreamsRequest.builder().logGroupName(logGroup).build())
        streams.filterNotNull().firstOrNull()?.logStreams()?.let { runInEdt { table.tableViewModel.items = it } }
    }
}
