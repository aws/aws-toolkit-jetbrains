// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import software.aws.toolkits.jetbrains.services.amazonq.profile.QEndpoints
import software.aws.toolkits.jetbrains.utils.rules.RegistryExtension

@ExtendWith(ApplicationExtension::class)
class QEndpointsTest : BasePlatformTestCase() {

    @JvmField
    @RegisterExtension
    val registryExtension = RegistryExtension()

    @Test
    fun `test default registry value and parse`() {
        val testJson = """
            [
              {"region": "us-east-1", "endpoint": "https://codewhisperer.us-east-1.amazonaws.com/"},
              {"region": "eu-central-1", "endpoint": "https://rts.prod-eu-central-1.codewhisperer.ai.aws.dev/"}
            ]
        """.trimIndent()

        registryExtension.setValue("amazon.q.endpoints.json", testJson)

        val parsed = QEndpoints.listRegionEndpoints()
        assertEquals(2, parsed.size)

        val iad = parsed.first { it.region == "us-east-1" }
        assertEquals("https://codewhisperer.us-east-1.amazonaws.com/", iad.endpoint)

        val fra = parsed.first { it.region == "eu-central-1" }
        assertEquals("https://rts.prod-eu-central-1.codewhisperer.ai.aws.dev/", fra.endpoint)
    }
}
