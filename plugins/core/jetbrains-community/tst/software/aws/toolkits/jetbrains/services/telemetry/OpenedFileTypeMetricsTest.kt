// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

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
        assertTrue(service.getOpenedFileTypes().contains(".kt") )
    }

    @Test
    fun `test addToExistingTelemetryBatch with disallowed extension`() {
        service.addToExistingTelemetryBatch("txt")
        assertEquals(service.getOpenedFileTypes(), emptySet<String>())
    }
}
