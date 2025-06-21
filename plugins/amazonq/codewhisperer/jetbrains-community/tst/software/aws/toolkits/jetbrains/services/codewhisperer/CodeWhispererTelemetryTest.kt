// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.telemetry.TelemetryPublisher
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.actions.Pause
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.actions.Resume
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.telemetry.NoOpPublisher
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.AwsSettings

class CodeWhispererTelemetryTest : CodeWhispererTestBase() {
    private val awsModifySetting = "aws_modifySetting"

    private class TestTelemetryService(
        publisher: TelemetryPublisher = NoOpPublisher(),
        batcher: TelemetryBatcher,
    ) : TelemetryService(publisher, batcher)

    private lateinit var telemetryService: TelemetryService
    private lateinit var batcher: TelemetryBatcher
    private lateinit var telemetryServiceSpy: TelemetryService
    private var isTelemetryEnabledDefault: Boolean = false

    @Before
    override fun setUp() {
        super.setUp()
        batcher = mock<TelemetryBatcher>()
        telemetryService = TestTelemetryService(batcher = batcher)
        telemetryServiceSpy = spy(telemetryService)
        ApplicationManager.getApplication().replaceService(TelemetryService::class.java, telemetryServiceSpy, disposableRule.disposable)
        isTelemetryEnabledDefault = AwsSettings.getInstance().isTelemetryEnabled
        AwsSettings.getInstance().isTelemetryEnabled = true
    }

    @Test
    fun `test toggle autoSuggestion will emit autoSuggestionActivation telemetry (popup)`() {
        val metricCaptor = argumentCaptor<MetricEvent>()
        doNothing().whenever(batcher).enqueue(metricCaptor.capture())

        Pause().actionPerformed(TestActionEvent { projectRule.project })
        assertEventsContainsFieldsAndCount(
            metricCaptor.allValues,
            awsModifySetting,
            1,
            "settingId" to CodeWhispererConstants.AutoSuggestion.SETTING_ID,
            "settingState" to CodeWhispererConstants.AutoSuggestion.DEACTIVATED
        )

        Resume().actionPerformed(TestActionEvent { projectRule.project })
        assertEventsContainsFieldsAndCount(
            metricCaptor.allValues,
            awsModifySetting,
            1,
            "settingId" to CodeWhispererConstants.AutoSuggestion.SETTING_ID,
            "settingState" to CodeWhispererConstants.AutoSuggestion.ACTIVATED
        )
        assertEventsContainsFieldsAndCount(
            metricCaptor.allValues,
            awsModifySetting,
            2,
        )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        telemetryService.dispose()
        AwsSettings.getInstance().isTelemetryEnabled = isTelemetryEnabledDefault
    }

    companion object {
        // TODO: move this to util and tweak it to show what telemetry field not matching assertions
        fun assertEventsContainsFieldsAndCount(
            events: Collection<MetricEvent>,
            name: String,
            count: Int,
            vararg keyValues: Pair<String, Any?>,
            atLeast: Boolean = false,
        ) {
            assertThat(events).filteredOn { event ->
                event.data.any {
                    it.name == name && isThisMapContains(it.metadata, *keyValues)
                }
            }.apply {
                if (atLeast) {
                    hasSizeGreaterThanOrEqualTo(count)
                } else {
                    hasSize(count)
                }
            }
        }

        private fun isThisMapContains(map: Map<String, String>, vararg keyValues: Pair<String, Any?>): Boolean {
            keyValues.forEach { pair ->
                val fieldName = pair.first
                val expectedValue = pair.second
                expectedValue?.let {
                    if (it.toString() != map[fieldName]) {
                        return false
                    }
                }
            }
            return true
        }
    }
}
