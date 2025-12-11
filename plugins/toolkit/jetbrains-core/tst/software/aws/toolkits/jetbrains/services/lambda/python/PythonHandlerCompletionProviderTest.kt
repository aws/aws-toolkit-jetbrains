// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.python

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.core.lambda.LambdaRuntime
import software.aws.toolkits.jetbrains.services.lambda.completion.HandlerCompletionProvider
import software.aws.toolkits.jetbrains.utils.rules.PythonCodeInsightTestFixtureRule

class PythonHandlerCompletionProviderTest {

    @Rule
    @JvmField
    val projectRule = PythonCodeInsightTestFixtureRule()

    @Test
    fun completionIsNotSupportedPython38() {
        val provider = HandlerCompletionProvider(projectRule.project, LambdaRuntime.PYTHON3_8)
        assertThat(provider.isCompletionSupported).isFalse()
    }

    @Test
    fun completionIsNotSupportedPython39() {
        val provider = HandlerCompletionProvider(projectRule.project, LambdaRuntime.PYTHON3_9)
        assertThat(provider.isCompletionSupported).isFalse()
    }

    @Test
    fun completionIsNotSupportedPython310() {
        val provider = HandlerCompletionProvider(projectRule.project, LambdaRuntime.PYTHON3_10)
        assertThat(provider.isCompletionSupported).isFalse()
    }

    @Test
    fun completionIsNotSupportedPython311() {
        val provider = HandlerCompletionProvider(projectRule.project, LambdaRuntime.PYTHON3_11)
        assertThat(provider.isCompletionSupported).isFalse()
    }

    @Test
    fun completionIsNotSupportedPython312() {
        val provider = HandlerCompletionProvider(projectRule.project, LambdaRuntime.PYTHON3_12)
        assertThat(provider.isCompletionSupported).isFalse()
    }

    @Test
    fun completionIsNotSupportedPython314() {
        val provider = HandlerCompletionProvider(projectRule.project, LambdaRuntime.PYTHON3_14)
        assertThat(provider.isCompletionSupported).isFalse()
    }
}
