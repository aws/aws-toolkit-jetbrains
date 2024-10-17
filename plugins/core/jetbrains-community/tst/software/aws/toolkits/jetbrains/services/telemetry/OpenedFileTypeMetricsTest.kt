// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.times

class OpenedFileTypeMetricsTest {

    private lateinit var service: OpenedFileTypesMetricsService

    @Before
    fun setup() {
        service = OpenedFileTypesMetricsService()
    }

    @After
    fun teardown() {
        service.dispose()
    }

    @Test
    fun `test addToExistingTelemetryBatch with allowed extension`() {
        service.addToExistingTelemetryBatch("kt")
        assert(service.getOpenedFileTypes().contains(".kt"))
    }

    @Test
    fun `test addToExistingTelemetryBatch with disallowed extension`() {
        service.addToExistingTelemetryBatch("txt")
        assert(service.getOpenedFileTypes().isEmpty())
    }
}
