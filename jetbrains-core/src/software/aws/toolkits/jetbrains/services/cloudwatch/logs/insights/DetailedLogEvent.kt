// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.SimpleToolWindowPanel
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.core.awsClient
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

class DetailedLogEvent(
    private val project: Project,
    private val logEventIdentifier: String
) : CoroutineScope by ApplicationThreadPoolScope("DetailedLogEvents"), Disposable {
    lateinit var logEventTable: SimpleToolWindowPanel
    lateinit var allLogFieldsPanel: JPanel
    //lateinit var showLogStreams: JButton
    lateinit var logEvent: JLabel
    val client: CloudWatchLogsClient = project.awsClient()
    private val resultsTable: LogEventTable = LogEventTable(project, client, logEventIdentifier)
    private fun createUIComponents() {
        // TODO: place custom component creation code here
        logEventTable = SimpleToolWindowPanel(false, true)
    }
    init {
        //showLogStreams.text = "Show corresponding log stream"
        logEvent.text = "Log Event"
        Disposer.register(this, resultsTable)
        logEventTable.setContent(resultsTable.component)
        loadResultsTable()
        /*showLogStreams.isEnabled = !QueryResultsActor.queryPtrToLogGroupLogStream.isNullOrEmpty()
        showLogStreams.addActionListener {
            QueryResultsActor.queryPtrToLogGroupLogStream[logEventIdentifier]?.get("log")?.let {
                it1 -> QueryResultsActor.queryPtrToLogGroupLogStream[logEventIdentifier]?.get("logStream")?.let {
                it2 -> CloudWatchLogWindow(project).showLogStream(it1, it2) } }
        }*/
    }

    private fun loadResultsTable(){
        launch { resultsTable.channel.send(LogEventActor.MessageLoadEvent.LoadLogEventResults) }
    }
    override fun dispose() {
    }
}
