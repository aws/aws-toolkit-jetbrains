// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.SimpleToolWindowPanel
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.core.awsClient
import javax.swing.JLabel
import javax.swing.JPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message

class DetailedLogEvent(
    private val project: Project,
    private val logEventIdentifier: String
) : CoroutineScope by ApplicationThreadPoolScope("DetailedLogEvents"), Disposable {
    lateinit var logEventTable: SimpleToolWindowPanel
    lateinit var allLogFieldsPanel: JPanel
    lateinit var logEvent: JLabel
    val client: CloudWatchLogsClient = project.awsClient()
    private val resultsTable: LogEventTable = LogEventTable(project, client, logEventIdentifier)
    private fun createUIComponents() {
        // TODO: place custom component creation code here
        logEventTable = SimpleToolWindowPanel(false, true)
    }
    init {
        logEvent.text = message("cloudwatch.logs.log_record_title")
        Disposer.register(this, resultsTable)
        logEventTable.setContent(resultsTable.component)
        loadResultsTable()
        // TODO: View Log Streams button
    }

    private fun loadResultsTable() {
        launch { resultsTable.channel.send(LogEventActor.MessageLoadEvent.LoadLogEventResults) }
    }
    override fun dispose() {
    }
}
