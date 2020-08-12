// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
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
        val fieldList = getFields(query)
        QueryResultsWindow.getInstance(project).showResults(queryId, fieldList)
    }

    fun getFields(query: String): List<String> {
        var fieldList = mutableListOf<List<String>>()

        val queries = query.split("|")
        for (item in queries){
            if (item.trim().startsWith("fields", ignoreCase = false)) {
                var fields = item.trim().substring(6)
                fieldList.add(fields.split(",").map { it.trim() })
            }
        }
        if(fieldList.isEmpty()){
            return listOf("@message", "@timestamp")
        }
        return fieldList.flatten()



       /* var fieldList: List<String>
        if (query.contains("fields", ignoreCase = true)) {
            var fields = query.substring(query.indexOf("fields", ignoreCase = true) + 7)
            if (fields.contains("|")) {
                fields = fields.substring(0, fields.indexOf("|"))
            }
            fieldList = fields.split(",").map { it.trim() }
        } else {
            fieldList = listOf("@message", "@timestamp")
        }
        return fieldList*/
    }
}
