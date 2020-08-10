// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsRequest
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule

class QueryResultsReturnedTest {
    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    @Test
    fun `Get-Query-Results returns log events resulting from the query`() {
        /*val client = mockClientManagerRule.create<CloudWatchLogsClient>()

        whenever(client.getQueryResults(Mockito.any<GetQueryResultsRequest>()))
            .thenReturn(
                GetQueryResultsResponse.builder().results()
            )*/
    }
}
