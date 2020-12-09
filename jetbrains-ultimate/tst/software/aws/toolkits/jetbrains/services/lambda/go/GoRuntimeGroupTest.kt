// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.setGoSdkVersion

class GoRuntimeGroupTest {
    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    private val sut = GoRuntimeGroup()

    @Test
    fun testRuntime1x() {
        projectRule.project.setGoSdkVersion("1.0.0")
        val runtime = sut.determineRuntime(projectRule.project)
        assertThat(runtime).isEqualTo(Runtime.GO1_X)
    }

    @Test
    fun testRuntime2x() {
        projectRule.project.setGoSdkVersion("2.0.0")
        val runtime = sut.determineRuntime(projectRule.project)
        assertThat(runtime).isEqualTo(null)
    }
}
