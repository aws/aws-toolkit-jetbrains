// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeQueriesRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryRequest
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

class QueryingLogGroups(private val project: Project) : CoroutineScope by ApplicationThreadPoolScope("ExecutingQuery") {
    fun executeStartQuery(queryStartEndDate: StartEndDate, logGroupNames: List<String>, query: String, client: CloudWatchLogsClient) = launch {
        // TODO: Multiple log groups queried (currently only a single log group can be selected and queried)
        val request = StartQueryRequest.builder()
            .endTime(queryStartEndDate.endDate.epochSecond)
            .logGroupName(logGroupNames[0])
            .queryString(query)
            .startTime(queryStartEndDate.startDate.epochSecond)
            .build()
        val response = client.startQuery(request)
        var queryId: String = response.queryId()
        getQueryResults(queryId, client)
    }

    private fun getQueryResults(queryId: String, client: CloudWatchLogsClient) = launch {
        var status = ""
        while(status!="Complete"){
            val requestCheckQueryCompletion=GetQueryResultsRequest.builder().queryId(queryId).build()
            val responseCheckQueryCompletion=client.getQueryResults(requestCheckQueryCompletion)
            status= responseCheckQueryCompletion.statusAsString()
            if(status=="Complete"){
                var queryList = responseCheckQueryCompletion.results()
                for (item in queryList){
                    for (item1 in item){
                        println(item1.field())
                    }

                }
                //QueryResultsWindow.getInstance(project).showResults(queryList, queryId)
            }

        }
        println("Done")

    }


}
