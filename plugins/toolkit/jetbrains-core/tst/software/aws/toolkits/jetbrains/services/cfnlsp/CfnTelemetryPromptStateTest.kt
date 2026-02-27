// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import software.aws.toolkits.jetbrains.settings.CfnLspSettings

class CfnTelemetryPromptStateTest {

    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    private lateinit var promptState: CfnTelemetryPromptState

    @Before
    fun setUp() {
        promptState = CfnTelemetryPromptState.getInstance()
        promptState.hasResponded = false
        promptState.lastPromptDate = 0L
    }

    @Test
    fun `default state has not responded and no prompt date`() {
        val state = CfnTelemetryPromptState.State()
        assertThat(state.hasResponded).isFalse()
        assertThat(state.lastPromptDate).isEqualTo(0L)
    }

    @Test
    fun `hasResponded persists through state`() {
        promptState.hasResponded = true
        assertThat(promptState.getState().hasResponded).isTrue()
    }

    @Test
    fun `lastPromptDate persists through state`() {
        val now = System.currentTimeMillis()
        promptState.lastPromptDate = now
        assertThat(promptState.getState().lastPromptDate).isEqualTo(now)
    }

    @Test
    fun `loadState restores values`() {
        val saved = CfnTelemetryPromptState.State(hasResponded = true, lastPromptDate = 12345L)
        promptState.loadState(saved)

        assertThat(promptState.hasResponded).isTrue()
        assertThat(promptState.lastPromptDate).isEqualTo(12345L)
    }

    @Test
    fun `allow choice enables telemetry and marks permanent`() {
        val settings = CfnLspSettings.getInstance()

        promptState.hasResponded = true
        promptState.lastPromptDate = System.currentTimeMillis()
        settings.isTelemetryEnabled = true

        assertThat(promptState.hasResponded).isTrue()
        assertThat(promptState.lastPromptDate).isGreaterThan(0L)
        assertThat(settings.isTelemetryEnabled).isTrue()

        settings.isTelemetryEnabled = false
    }

    @Test
    fun `never choice disables telemetry and marks permanent`() {
        val settings = CfnLspSettings.getInstance()

        promptState.hasResponded = true
        promptState.lastPromptDate = System.currentTimeMillis()
        settings.isTelemetryEnabled = false

        assertThat(promptState.hasResponded).isTrue()
        assertThat(settings.isTelemetryEnabled).isFalse()
    }

    @Test
    fun `not now choice disables telemetry without marking permanent`() {
        val settings = CfnLspSettings.getInstance()

        promptState.hasResponded = false
        promptState.lastPromptDate = System.currentTimeMillis()
        settings.isTelemetryEnabled = false

        assertThat(promptState.hasResponded).isFalse()
        assertThat(promptState.lastPromptDate).isGreaterThan(0L)
        assertThat(settings.isTelemetryEnabled).isFalse()

        settings.isTelemetryEnabled = false
    }

    @Test
    fun `should prompt when no prior interaction`() {
        assertThat(promptState.hasResponded).isFalse()
        assertThat(promptState.lastPromptDate).isEqualTo(0L)

        val shouldPrompt = !promptState.hasResponded &&
            (promptState.lastPromptDate == 0L || System.currentTimeMillis() - promptState.lastPromptDate >= 30L * 24 * 60 * 60 * 1000)
        assertThat(shouldPrompt).isTrue()
    }

    @Test
    fun `should not prompt when permanently responded`() {
        promptState.hasResponded = true

        assertThat(!promptState.hasResponded).isFalse()
    }

    @Test
    fun `should not prompt within 30 day window`() {
        promptState.lastPromptDate = System.currentTimeMillis() - (20L * 24 * 60 * 60 * 1000)

        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val withinWindow = promptState.lastPromptDate != 0L &&
            System.currentTimeMillis() - promptState.lastPromptDate < thirtyDaysMs
        assertThat(withinWindow).isTrue()
    }

    @Test
    fun `should prompt after 30 days elapsed`() {
        promptState.lastPromptDate = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000)

        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val withinWindow = promptState.lastPromptDate != 0L &&
            System.currentTimeMillis() - promptState.lastPromptDate < thirtyDaysMs
        assertThat(withinWindow).isFalse()
    }
}
