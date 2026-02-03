// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class CfnLspSettingsTest {

    @Rule
    @JvmField
    val applicationRule = ApplicationRule()

    @Test
    fun `default settings have expected values`() {
        val settings = CfnLspSettings.getInstance()

        assertThat(settings.nodeRuntimePath).isEmpty()
        assertThat(settings.isTelemetryEnabled).isFalse()
        assertThat(settings.isHoverEnabled).isTrue()
        assertThat(settings.isCompletionEnabled).isTrue()
        assertThat(settings.maxCompletions).isEqualTo(100)
    }

    @Test
    fun `cfn-lint default settings`() {
        val settings = CfnLspSettings.getInstance()

        assertThat(settings.isCfnLintEnabled).isTrue()
        assertThat(settings.cfnLintLintOnChange).isTrue()
        assertThat(settings.cfnLintDelayMs).isEqualTo(3000)
        assertThat(settings.cfnLintIncludeChecks).isEqualTo("I")
        assertThat(settings.cfnLintIncludeExperimental).isFalse()
    }

    @Test
    fun `cfn-guard default settings`() {
        val settings = CfnLspSettings.getInstance()

        assertThat(settings.isCfnGuardEnabled).isTrue()
        assertThat(settings.cfnGuardValidateOnChange).isTrue()
        assertThat(settings.cfnGuardEnabledRulePacks).isEqualTo("wa-Security-Pillar")
        assertThat(settings.cfnGuardRulesFile).isEmpty()
    }

    @Test
    fun `settings can be modified`() {
        val settings = CfnLspSettings.getInstance()

        settings.nodeRuntimePath = "/usr/bin/node"
        assertThat(settings.nodeRuntimePath).isEqualTo("/usr/bin/node")

        settings.maxCompletions = 50
        assertThat(settings.maxCompletions).isEqualTo(50)

        // Reset to defaults
        settings.nodeRuntimePath = ""
        settings.maxCompletions = 100
    }

    @Test
    fun `guard rule packs list is populated`() {
        assertThat(CfnLspSettings.GUARD_RULE_PACKS).isNotEmpty()
        assertThat(CfnLspSettings.GUARD_RULE_PACKS).contains("wa-Security-Pillar")
        assertThat(CfnLspSettings.GUARD_RULE_PACKS).contains("hipaa-security")
        assertThat(CfnLspSettings.GUARD_RULE_PACKS).contains("cis-aws-benchmark-level-1")
    }
}
