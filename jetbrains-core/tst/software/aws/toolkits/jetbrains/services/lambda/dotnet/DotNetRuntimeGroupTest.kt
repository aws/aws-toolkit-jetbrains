// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.dotnet

import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.utils.rules.DotNetCodeInsightTestFixtureRule

class DotNetRuntimeGroupTest {

    @Rule
    @JvmField
    val projectRule = DotNetCodeInsightTestFixtureRule()

    private val sut = DotNetRuntimeGroup()

    /**
     * Sdk for DotNet is not defined in Idea API. We check that we cannot get any values here.
     */
    @Test
    fun testRuntimeDetectionNull() {
        val project = projectRule.project
        Assertions.assertThat(sut.determineRuntime(project)).isEqualTo(null)
    }
}
