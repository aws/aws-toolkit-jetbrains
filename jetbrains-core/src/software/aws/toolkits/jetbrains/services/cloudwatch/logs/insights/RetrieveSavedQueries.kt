// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeQueryDefinitionsRequest
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope

class RetrieveSavedQueries(private val client: CloudWatchLogsClient) :
    CoroutineScope by ApplicationThreadPoolScope("ExecutingQuery") {

    fun getSavedQueries() = launch {
        val request = DescribeQueryDefinitionsRequest.builder().build()
        val response = client.describeQueryDefinitions(request)
        val savedQueries =
            response.queryDefinitions().map { result -> result.name() to mapOf("query" to result.queryString(), "logGroups" to result.logGroupNames()) }
                .toMap()
        allQueries = savedQueries + sampleQueries
    }

    companion object {
        var allQueries = sampleQueries
    }
}
