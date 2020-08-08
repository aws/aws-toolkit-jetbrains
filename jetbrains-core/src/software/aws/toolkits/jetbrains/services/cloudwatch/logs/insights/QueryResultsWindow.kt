// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowManager
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowType
import software.aws.toolkits.jetbrains.services.cloudwatch.logs.CloudWatchLogWindow
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result

class QueryResultsWindow(private val project: Project) : CoroutineScope by ApplicationThreadPoolScope("openResultsWindow") {
    private val toolWindow = ToolkitToolWindowManager.getInstance(project, QueryResultsWindow.INSIGHTS_RESULTS_TOOL_WINDOW)
    private val edtContext = getCoroutineUiContext()
    fun showResults (resultList: List<MutableList<ResultField>>, queryId: String, fieldList: List<String>) = launch {
        try{
            println("QueryResultsWindow")
            val existingWindow = toolWindow.find(queryId)
            if (existingWindow != null) {
                withContext(edtContext) {
                    existingWindow.show()
                }
                return@launch
            }

            val group = QueryResultList(project, resultList, fieldList)
            val title = message("cloudwatch.logs.query_results_title")
            withContext(edtContext) {
                toolWindow.addTab(title, group.resultsPanel, activate = true, id = queryId, disposable = group)
            }
        } catch(e: Exception) {
            throw e
        }


    }
    companion object{
        internal val INSIGHTS_RESULTS_TOOL_WINDOW = ToolkitToolWindowType(
            "AWS.InsightsResultsTable",
             "CloudWatch Logs Insights"
        )
        fun getInstance(project: Project)= ServiceManager.getService(project, QueryResultsWindow::class.java)
    }

}
