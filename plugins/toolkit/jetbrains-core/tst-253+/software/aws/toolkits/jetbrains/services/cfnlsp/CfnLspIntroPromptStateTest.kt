// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CfnLspIntroPromptStateTest {

    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    private lateinit var promptState: CfnLspIntroPromptState

    @Before
    fun setUp() {
        promptState = CfnLspIntroPromptState.getInstance()
        promptState.loadState(CfnLspIntroPromptStateData(hasResponded = false))
    }

    @Test
    fun `default state has not responded`() {
        val state = CfnLspIntroPromptStateData()
        assertThat(state.hasResponded).isFalse()
    }

    @Test
    fun `hasResponded persists through state`() {
        promptState.setResponded()
        assertThat(promptState.getState().hasResponded).isTrue()
    }

    @Test
    fun `loadState restores values`() {
        promptState.loadState(CfnLspIntroPromptStateData(hasResponded = true))
        assertThat(promptState.hasResponded()).isTrue()
    }

    @Test
    fun `explore choice marks permanent response`() {
        promptState.setResponded()
        assertThat(promptState.hasResponded()).isTrue()
    }

    @Test
    fun `dont show again choice marks permanent response`() {
        promptState.setResponded()
        assertThat(promptState.hasResponded()).isTrue()
    }

    @Test
    fun `should prompt when no prior interaction`() {
        assertThat(promptState.hasResponded()).isFalse()

        val shouldPrompt = !promptState.hasResponded()
        assertThat(shouldPrompt).isTrue()
    }

    @Test
    fun `should not prompt when permanently responded`() {
        promptState.setResponded()
        assertThat(promptState.hasResponded()).isTrue()
    }
}
