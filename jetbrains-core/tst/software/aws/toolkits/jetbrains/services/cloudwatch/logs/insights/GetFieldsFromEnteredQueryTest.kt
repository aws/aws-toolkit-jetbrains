// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudwatch.logs.insights

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule

class GetFieldsFromEnteredQueryTest {
    @JvmField
    @Rule
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Test
    fun `Fields extracted correctly from query string`() {
        val query = QueryingLogGroups(projectRule.project)
        val firstQuery = "filter @message like /Error/ | fields @message"
        val secondQuery = "filter @message like /Error/"
        val thirdQuery = "fields @logStream, @timestamp"
        assertThat(query.getFields(firstQuery)).isEqualTo(listOf("@message"))
        assertThat(query.getFields(secondQuery)).isEqualTo(listOf("@message", "@timestamp"))
        assertThat(query.getFields(thirdQuery)).isEqualTo(listOf("@logStream", "@timestamp"))
    }
}
