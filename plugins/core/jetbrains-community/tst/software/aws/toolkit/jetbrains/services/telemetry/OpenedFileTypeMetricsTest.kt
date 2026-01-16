// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.services.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenedFileTypeMetricsTest {

    private lateinit var service: OpenedFileTypesMetricsService

    @BeforeEach
    fun setup() {
        service = OpenedFileTypesMetricsService()
    }

    @AfterEach
    fun teardown() {
        service.dispose()
    }

    @Test
    fun `test addToExistingTelemetryBatch with allowed extension`() {
        service.addToExistingTelemetryBatch("kt")
        assertThat(service.getOpenedFileTypes()).contains(".kt")
    }

    @Test
    fun `test addToExistingTelemetryBatch with disallowed extension`() {
        service.addToExistingTelemetryBatch("mp4")
        assertThat(service.getOpenedFileTypes()).isEmpty()
    }
}
