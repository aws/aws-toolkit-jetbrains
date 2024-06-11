// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.codemodernizer.model

import com.intellij.testFramework.ApplicationRule
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test

open class CodeTransformTelemetryServiceTest {
    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    @Test
    fun `CodeTransformTelemetryMetadata will set values and resets defaults properly`() {
        CodeTransformTelemetryService.setDependencyVersionSelected("1.2.3")
        CodeTransformTelemetryService.setCancelledFromChat(true)
        assertEquals(CodeTransformTelemetryService.getInstance().dependencyVersionSelected, "1.2.3")
        assertEquals(CodeTransformTelemetryService.getInstance().cancelledFromChat, true)

        // check reset defaults works
        CodeTransformTelemetryService.getInstance().resetDefaults()
        assertEquals(CodeTransformTelemetryService.getInstance().dependencyVersionSelected, null)
        assertEquals(CodeTransformTelemetryService.getInstance().cancelledFromChat, false)
    }

    @Test
    fun `CodeTransformTelemetryMetadataSingletonTest toJsonString() will serialize object correctly`() {
        CodeTransformTelemetryService.setDependencyVersionSelected("1.2.3")
        CodeTransformTelemetryService.setCancelledFromChat(true)
        val expectedJsonString = """{"dependencyVersionSelected":"1.2.3","cancelledFromChat":true}"""
        assertEquals(expectedJsonString, CodeTransformTelemetryService.getInstance().toJsonString())
    }

    @Test
    fun `CodeTransformTelemetryMetadataSingletonTest trimJsonString() trims single field JSON string to specified length`() {
        val longString = "a".repeat(CODETRANSFORM_METADATA_MAX_STRINGIFIED_LENGTH)
        CodeTransformTelemetryService.setDependencyVersionSelected(longString)
        CodeTransformTelemetryService.setCancelledFromChat(true)

        val expectedTrimmedJsonString = """{"dependencyVersionSelected":"${"a".repeat(MAX_SERIALIZABLE_STRING_LENGTH)}","cancelledFromChat":true}"""
        assertEquals(expectedTrimmedJsonString, CodeTransformTelemetryService.getInstance().toJsonString())
    }
}
