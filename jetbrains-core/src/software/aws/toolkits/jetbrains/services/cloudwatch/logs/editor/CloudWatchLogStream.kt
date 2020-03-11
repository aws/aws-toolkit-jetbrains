// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.runUnlessDisposed
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.jetbrains.core.credentials.activeCredentialProvider
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogStreamClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField

class CloudWatchLogStream(
    private val project: Project,
    private val logGroup: String,
    private val logStream: String,
    fromHead: Boolean,
    startTime: Long? = null,
    timeScale: Long? = null
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsGroup"), Disposable {
    lateinit var content: JPanel
    lateinit var logsPanel: JScrollPane
    lateinit var searchLabel: JLabel
    lateinit var searchField: JTextField
    lateinit var showAsButton: JButton
    lateinit var unwrapButton: JButton
    lateinit var streamLogsOn: JButton
    lateinit var streamLogsOff: JButton

    val title = message("cloudwatch.logs.log_stream_title", logStream)
    private val edtContext = getCoroutineUiContext(disposable = this)
    private val streamingLogs: AtomicBoolean = AtomicBoolean(false)
    private var logStreamingJob: Deferred<*>? = null

    private lateinit var defaultColumnInfo: Array<ColumnInfo<OutputLogEvent, String>>
    private val wrappingColumnInfo = arrayOf(LogStreamColumnDate(), LogStreamWrappingColumn())

    private lateinit var logsTable: JBTable
    private val logStreamClient = CloudWatchLogStreamClient(project, logGroup, logStream)

    private fun createUIComponents() {
        defaultColumnInfo = arrayOf(LogStreamColumnDate(), LogStreamColumn())

        val model = ListTableModel<OutputLogEvent>(defaultColumnInfo, mutableListOf<OutputLogEvent>())
        logsTable = JBTable(model).apply {
            setPaintBusy(true)
            autoscrolls = true
            emptyText.text = message("loading_resource.loading")
            tableHeader.reorderingAllowed = false
        }
        // TODO fix resizing
        logsTable.columnModel.getColumn(0).preferredWidth = 150
        logsTable.columnModel.getColumn(0).maxWidth = 150
        logsTable.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        logsPanel = ScrollPaneFactory.createScrollPane(logsTable)
    }

    init {
        searchLabel.text = "${project.activeCredentialProvider().displayName} => ${project.activeRegion().displayName} => $logGroup => $logStream"
        logsTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        logsPanel.verticalScrollBar.addAdjustmentListener {
            if (logsTable.model.rowCount == 0) {
                return@addAdjustmentListener
            }
            if (logsPanel.verticalScrollBar.isAtBottom()) {
                launch {
                    val items = logStreamClient.loadMoreForward()
                    if (items.isNotEmpty()) {
                        val events = logsTable.logsModel.items.plus(items)
                        withContext(edtContext) { logsTable.logsModel.items = events }
                    }
                }
            } else if (logsPanel.verticalScrollBar.isAtTop()) {
                launch {
                    val items = logStreamClient.loadMoreBackward()
                    if (items.isNotEmpty()) {
                        val events = items.plus(logsTable.logsModel.items)
                        withContext(edtContext) { logsTable.logsModel.items = events }
                    }
                }
            }
        }
        launch {
            runUnlessDisposed(this@CloudWatchLogStream) {
                try {
                    val items = if (startTime != null && timeScale != null) {
                        logStreamClient.loadInitialAround(startTime, timeScale)
                    } else {
                        logStreamClient.loadInitial(fromHead)
                    }
                    withContext(edtContext) {
                        logsTable.emptyText.text = message("cloudwatch.logs.no_events")
                        logsTable.logsModel.items = items
                        logsTable.setPaintBusy(false)
                    }
                } catch (e: Exception) {
                    val errorMessage = message("cloudwatch.logs.failed_to_load_stream", logStream)
                    CloudWatchLogGroup.LOG.error(e) { errorMessage }
                    notifyError(title = errorMessage, project = project)
                    withContext(edtContext) { logsTable.emptyText.text = errorMessage }
                }
            }
        }
        setUpTemporaryButtons()
        // addActions()
    }

    private fun setUpTemporaryButtons() {
        showAsButton.addActionListener {
            launch(edtContext) {
                logsTable.logsModel.columnInfos = wrappingColumnInfo
            }
        }
        unwrapButton.addActionListener {
            launch(edtContext) {
                logsTable.logsModel.columnInfos = defaultColumnInfo
            }
        }
        streamLogsOn.addActionListener {
            val alreadyStreaming = streamingLogs.getAndSet(true)
            if (alreadyStreaming) {
                return@addActionListener
            }
            logStreamingJob = async {
                runUnlessDisposed(this@CloudWatchLogStream) {
                    while (true) {
                        val items = logStreamClient.loadMoreForward()
                        if (items.isNotEmpty()) {
                            val events = logsTable.logsModel.items.plus(items)
                            withContext(edtContext) { logsTable.logsModel.items = events }
                        }
                        delay(1000L)
                    }
                }
            }
        }
        streamLogsOff.addActionListener {
            launch {
                try {
                    logStreamingJob?.cancelAndJoin()
                } finally {
                    streamingLogs.set(false)
                }
            }
        }
    }

    /* will be added in the next PR but less annoying to comment out
    private fun addActions() {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(OpenCurrentInEditor(project, logStream, logsTable.logsModel))
        actionGroup.add(Separator())
        actionGroup.add(ShowLogsAroundGroup(logGroup, logStream, logsTable))
        PopupHandler.installPopupHandler(
            logsTable,
            actionGroup,
            ActionPlaces.EDITOR_POPUP,
            ActionManager.getInstance()
        )
    }
    */

    override fun dispose() {}

    private fun JScrollBar.isAtBottom(): Boolean = value == (maximum - visibleAmount)
    private fun JScrollBar.isAtTop(): Boolean = value == minimum
    private val JBTable.logsModel: ListTableModel<OutputLogEvent> get() = this.model as ListTableModel<OutputLogEvent>
}
