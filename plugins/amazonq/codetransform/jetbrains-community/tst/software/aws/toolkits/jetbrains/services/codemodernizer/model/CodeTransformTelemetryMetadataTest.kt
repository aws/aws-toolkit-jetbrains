// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.model

import junit.framework.TestCase.assertEquals
import org.junit.Test

class CodeTransformTelemetryMetadataTest {

    @Test
    fun `test toJsonString serializes object correctly`() {
        val metadata = CodeTransformTelemetryMetadata(
            dependencyVersionSelected = "1.2.3",
            cancelledFromChat = true
        )

        val expectedJsonString = """{"dependencyVersionSelected":"1.2.3","cancelledFromChat":true}"""
        assertEquals(expectedJsonString, metadata.toJsonString())
    }

    @Test
    fun `test trimJsonString trims JSON string to specified length`() {
        val longString = "a".repeat(MAX_STRINGIFIED_LENGTH + 1000)
        val metadata = CodeTransformTelemetryMetadata(
            dependencyVersionSelected = longString,
            cancelledFromChat = true
        )
        val expectedTrimmedJsonString = """{"dependencyVersionSelected":"${longString.substring(0, MAX_STRINGIFIED_LENGTH - 30)}","cancelledFromChat":true}"""
        assertEquals(expectedTrimmedJsonString, metadata.toJsonString())
    }
}
