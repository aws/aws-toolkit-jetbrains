// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.testFramework.ApplicationExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.spy
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.jetbrains.services.telemetry.MockTelemetryServiceExtension
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService

@ExtendWith(ApplicationExtension::class)
class AwsSettingsTest {
    @JvmField
    @RegisterExtension
    val mockTelemetryService = MockTelemetryServiceExtension()

    private lateinit var telemetryService: TelemetryService
    private lateinit var batcher: TelemetryBatcher
    private lateinit var awsSettings: DefaultAwsSettings
    private lateinit var awsConfiguration: AwsConfiguration

    @BeforeEach
    fun setup() {
        batcher = mockTelemetryService.batcher()
        telemetryService = mockTelemetryService.telemetryService()
        awsSettings = spy(DefaultAwsSettings())
        awsConfiguration = spy(AwsConfiguration())
        awsSettings.loadState(awsConfiguration)
    }

    @Test
    fun `telemetry event batched before setting isTelemetryEnabled to false`() {
        verifyTelemetryEventOrder(false)
    }

    @Test
    fun `telemetry event batched before setting isTelemetryEnabled to true`() {
        verifyTelemetryEventOrder(true)
    }

    private fun verifyTelemetryEventOrder(value: Boolean) {
        val inOrder = inOrder(telemetryService, batcher, awsConfiguration)
        val changeCaptor = argumentCaptor<Boolean>()
        val onChangeEventCaptor = argumentCaptor<(Boolean) -> Unit>()

        awsSettings.isTelemetryEnabled = value

        inOrder.verify(telemetryService).setTelemetryEnabled(changeCaptor.capture(), onChangeEventCaptor.capture())
        assertThat(changeCaptor.firstValue).isEqualTo(value)
        inOrder.verify(batcher).onTelemetryEnabledChanged(value, onChangeEventCaptor.firstValue)
        inOrder.verify(awsConfiguration).isTelemetryEnabled = value
    }
}
