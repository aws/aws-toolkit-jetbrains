// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.goide.sdk.GoSdkService
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.lambda.LambdaRuntime
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.createMockSdk

@Ignore("Setting the Go SDK causes some weird behavior to occur after the project rule is disposed")
class GoRuntimeGroupTest {
    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    private val sut = GoRuntimeGroup()

    @Test
    fun runtimeForSdk() {
        val sdk = createMockSdk(projectRule.module.moduleFilePath, "1.0.0")
        runInEdtAndWait {
            GoSdkService.getInstance(projectRule.project).setSdk(sdk)
        }
        val module = projectRule.module
        val runtime = sut.determineRuntime(module)
        assertThat(runtime).isEqualTo(LambdaRuntime.GO1_X)
    }
}
