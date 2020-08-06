// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListTableModel
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class QueryResultsTable(
    private val project: Project,
    private val queryResults: List<MutableList<ResultField>>,
    private val client: CloudWatchLogsClient,
    private val fieldList: List<String>
) : CoroutineScope by ApplicationThreadPoolScope("QueryResultsTable"), Disposable {
    val component: JComponent = TODO()
    private val resultsTable: TableView<List<ResultField>>

    init{
            for (field in fieldList){

            }
            val  tableModel = ListTableModel(
                arrayOf(ColumnInfoDetails(fieldList[0])), mutableListOf<List<ResultField>>()
            )
        resultsTable = TableView(tableModel).apply {
            setPaintBusy(true)
            autoscrolls = true
            emptyText.text = message("loading_resource.loading")
            tableHeader.reorderingAllowed = false
            tableHeader.resizingAllowed = false
        }

        
    }
    override fun dispose() {

    }
}
