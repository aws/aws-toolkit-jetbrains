// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.codemodernizer.model

import junit.framework.TestCase.assertEquals
import org.junit.Test

open class CodeTransformTelemetryMetadataSingletonTest {
    @Test
    fun `test sets values and resets defaults properly`() {
        CodeTransformTelemetryMetadataSingleton.setDependencyVersionSelected("1.2.3")
        CodeTransformTelemetryMetadataSingleton.setCancelledFromChat(true)
        assertEquals(CodeTransformTelemetryMetadataSingleton.getInstance().dependencyVersionSelected, "1.2.3")
        assertEquals(CodeTransformTelemetryMetadataSingleton.getInstance().cancelledFromChat, true)

        CodeTransformTelemetryMetadataSingleton.getInstance().resetDefaults()
        assertEquals(CodeTransformTelemetryMetadataSingleton.getInstance().dependencyVersionSelected, null)
        assertEquals(CodeTransformTelemetryMetadataSingleton.getInstance().cancelledFromChat, false)
    }

    @Test
    fun `test toJsonString serializes object correctly`() {
        CodeTransformTelemetryMetadataSingleton.setDependencyVersionSelected("1.2.3")
        CodeTransformTelemetryMetadataSingleton.setCancelledFromChat(true)
        val expectedJsonString = """{"dependencyVersionSelected":"1.2.3","cancelledFromChat":true}"""
        assertEquals(expectedJsonString, CodeTransformTelemetryMetadataSingleton.getInstance().toJsonString())
    }

    @Test
    fun `test trimJsonString trims JSON string to specified length`() {
        val longString = "a".repeat(CODETRANSFORM_METADATA_MAX_STRINGIFIED_LENGTH + 1000)
        CodeTransformTelemetryMetadataSingleton.setDependencyVersionSelected(longString)
        CodeTransformTelemetryMetadataSingleton.setCancelledFromChat(true)

        val expectedTrimmedJsonString = """{"cancelledFromChat":true}"""
        assertEquals(expectedTrimmedJsonString, CodeTransformTelemetryMetadataSingleton.getInstance().toJsonString())
    }
}
