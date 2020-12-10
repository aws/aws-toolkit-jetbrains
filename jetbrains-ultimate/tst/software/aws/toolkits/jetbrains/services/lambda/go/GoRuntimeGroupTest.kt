// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.go

import com.intellij.openapi.roots.ModuleRootModificationUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.utils.rules.GoCodeInsightTestFixtureRule
import software.aws.toolkits.jetbrains.utils.rules.createMockSdk

class GoRuntimeGroupTest {
    @Rule
    @JvmField
    val projectRule = GoCodeInsightTestFixtureRule()

    private val sut = GoRuntimeGroup()

    @Test
    fun testRuntime0x() {
        val module = projectRule.module
        ModuleRootModificationUtil.setModuleSdk(module, createMockSdk("0.99.99"))

        val runtime = sut.determineRuntime(module)
        assertThat(runtime).isEqualTo(null)
    }

    @Test
    fun testRuntime1x() {
        val module = projectRule.module
        ModuleRootModificationUtil.setModuleSdk(module, createMockSdk("1.0.0"))

        val runtime = sut.determineRuntime(module)
        assertThat(runtime).isEqualTo(Runtime.GO1_X)
    }

    @Test
    fun testRuntime2x() {
        val module = projectRule.module
        ModuleRootModificationUtil.setModuleSdk(module, createMockSdk("2.0.0"))

        val runtime = sut.determineRuntime(module)
        assertThat(runtime).isEqualTo(null)
    }
}
