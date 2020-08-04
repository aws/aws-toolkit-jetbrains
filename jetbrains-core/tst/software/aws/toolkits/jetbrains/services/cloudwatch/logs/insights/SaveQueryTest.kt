// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import software.aws.toolkits.resources.message
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.aws.toolkits.jetbrains.core.MockClientManagerRule

@RunsInEdt
class SaveQueryTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()
    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)
    private lateinit var client: CloudWatchLogsClient
    private lateinit var view: EnterQueryName
    private lateinit var validator: SaveQueryDialog

    @Test
    fun `Query name entered`() {
        runInEdtAndWait {
            val project = projectRule.project
            view = EnterQueryName(project)
            client = mockClientManagerRule.create()
            validator = SaveQueryDialog(project, "fields @timestamp", listOf("log1"), client)
            view.queryName.text = ""
            assertThat(validator.validateQueryName(view)?.message).contains(message("cloudwatch.logs.query_name"))
        }
    }
}
