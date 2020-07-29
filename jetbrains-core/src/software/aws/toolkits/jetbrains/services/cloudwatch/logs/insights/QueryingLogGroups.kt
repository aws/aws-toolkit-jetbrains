// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryRequest
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import java.time.Instant

class QueryingLogGroups(private val project: Project) : CoroutineScope by ApplicationThreadPoolScope("ExecutingQuery") {
    private var client: CloudWatchLogsClient = project.awsClient()
    fun executeStartQuery(startDate: Instant, endDate: Instant, logGroupNames: MutableList<String>, query: String) = launch {
        val request = StartQueryRequest.builder()
            .endTime(endDate.epochSecond)
            .logGroupName(logGroupNames[0])
            .queryString(query)
            .startTime(startDate.epochSecond)
            .build()
        val response = client.startQuery(request)
        val queryId = response.queryId()

        // TODO: Get the results of the query with qid
    }
}
