// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class QueryResultList(
    private val project: Project,
    private val response: List<MutableList<ResultField>>,
    private val fieldList : List<String>
) : CoroutineScope by ApplicationThreadPoolScope("CloudWatchLogsGroup"), Disposable {
    lateinit var resultsPanel: JPanel
    private lateinit var tablePanel: SimpleToolWindowPanel
    private lateinit var openQueryEditor: JButton
    private lateinit var resultsTitle: JLabel
    private val edtContext = getCoroutineUiContext(disposable = this)
    val client: CloudWatchLogsClient = project.awsClient()
    private val resultsTable: QueryResultsTable = QueryResultsTable(project, response, client, fieldList)
    private fun createUIComponents() {
        // TODO: place custom component creation code here
        tablePanel = SimpleToolWindowPanel(false, true)
    }
    init {
        println("QueryResultsList")
        Disposer.register(this,resultsTable)
        tablePanel.setContent(resultsTable.component)
        refreshTable()
    }
    private fun refreshTable(){
        launch {resultsTable.channel.send(QueryActor.MessageLoadQueryResults.LoadInitialQueryResults)}
    }
    override fun dispose() {
    }

}
