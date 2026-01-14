// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.testFramework.ApplicationExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.q.jetbrains.utils.satisfiesKt
import software.aws.toolkits.jetbrains.services.amazonq.profile.QDefaultServiceConfig
import software.aws.toolkits.jetbrains.services.amazonq.profile.QEndpoints
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionEndpoint
import software.amazon.q.jetbrains.utils.rules.RegistryExtension

@ExtendWith(ApplicationExtension::class)
class QEndpointsTest {

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
        assertThat(parsed).hasSize(2)
            .satisfiesKt { endpoints ->
                assertThat(endpoints).satisfiesExactlyInAnyOrder(
                    { assertThat(it).isEqualTo(QRegionEndpoint("us-east-1", "https://codewhisperer.us-east-1.amazonaws.com/")) },
                    { assertThat(it).isEqualTo(QRegionEndpoint("eu-central-1", "https://rts.prod-eu-central-1.codewhisperer.ai.aws.dev/")) },
                )
            }
    }

    @Test
    fun `uses default entries if blank`() {
        registryExtension.setValue("amazon.q.endpoints.json", "")

        assertThat(QEndpoints.listRegionEndpoints()).isEqualTo(QDefaultServiceConfig.ENDPOINT_MAP.toEndpointList())
    }

    @Test
    fun `uses default entries if invalid`() {
        registryExtension.setValue("amazon.q.endpoints.json", "asdfadfkajdklf32.4;'2l4;234l23.424';1l1!!@#!")

        assertThat(QEndpoints.listRegionEndpoints()).isEqualTo(QDefaultServiceConfig.ENDPOINT_MAP.toEndpointList())
    }

    private fun Map<String, String>.toEndpointList() = map { (region, endpoint) -> QRegionEndpoint(region, endpoint) }
}
