// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class CfnManifestAdapterTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `selects the requested environment`() {
        val json = mapper.writeValueAsString(
            mapOf(
                "alpha" to listOf(versionEntry("1.0.0-alpha")),
                "prod" to listOf(versionEntry("1.0.0")),
            )
        )

        val result = CfnManifestAdapter(CfnLspEnvironment.ALPHA).parseManifest(json)

        assertThat(result.versions.map { it.serverVersion }).containsExactly("1.0.0-alpha")
    }

    @Test
    fun `errors when neither the requested environment nor versions exists`() {
        val json = mapper.writeValueAsString(mapOf("alpha" to listOf(versionEntry("1.0.0-alpha"))))

        assertThatThrownBy { CfnManifestAdapter(CfnLspEnvironment.PROD).parseManifest(json) }
            .hasMessageContaining("environment 'prod'")
    }

    @Test
    fun `explicit environment override takes precedence`() {
        assertThat(
            detectCfnEnvironment(
                environmentOverride = " alpha ",
                isAutomation = true,
                isUnitTestMode = true,
            )
        ).isEqualTo(CfnLspEnvironment.ALPHA)
    }

    @Test
    fun `automatic environment selection uses beta for automation and tests and prod otherwise`() {
        assertThat(detectCfnEnvironment(null, isAutomation = true, isUnitTestMode = false)).isEqualTo(CfnLspEnvironment.BETA)
        assertThat(detectCfnEnvironment(null, isAutomation = false, isUnitTestMode = true)).isEqualTo(CfnLspEnvironment.BETA)
        assertThat(detectCfnEnvironment(null, isAutomation = false, isUnitTestMode = false)).isEqualTo(CfnLspEnvironment.PROD)
    }

    @Test
    fun `invalid environment override falls back to automatic selection`() {
        assertThat(detectCfnEnvironment("invalid", isAutomation = false, isUnitTestMode = false)).isEqualTo(CfnLspEnvironment.PROD)
    }

    private fun versionEntry(version: String): Map<String, Any> = mapOf(
        "serverVersion" to version,
        "latest" to false,
        "isDelisted" to false,
        "targets" to emptyList<Any>()
    )
}
