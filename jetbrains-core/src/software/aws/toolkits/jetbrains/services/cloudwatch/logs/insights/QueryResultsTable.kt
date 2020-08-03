// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import javax.swing.JComponent

class QueryResultsTable(
    private val project: Project,
    private val logGroup: List<MutableList<ResultField>>,
    private val client: CloudWatchLogsClient
) : CoroutineScope by ApplicationThreadPoolScope("QueryResultsTable"), Disposable {
    val component: JComponent = TODO()
    private val resultsTable: TableView<GetQueryResultsResponse>
    init{

    }
    override fun dispose() {

    }
}
