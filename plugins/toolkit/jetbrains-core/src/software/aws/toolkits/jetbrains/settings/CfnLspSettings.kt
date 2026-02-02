// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic

fun interface CfnLspSettingsChangeListener {
    fun settingsChanged()

    companion object {
        val TOPIC = Topic.create("CFN LSP Settings Changed", CfnLspSettingsChangeListener::class.java)
    }
}

@Service
@State(name = "cfnLspSettings", storages = [Storage("aws.xml", roamingType = RoamingType.DISABLED)])
internal class CfnLspSettings : PersistentStateComponent<CfnLspSettings.State> {
    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    fun notifySettingsChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(CfnLspSettingsChangeListener.TOPIC)
            .settingsChanged()
    }

    var nodeRuntimePath: String
        get() = state.nodeRuntimePath
        set(value) { state.nodeRuntimePath = value }

    var isTelemetryEnabled: Boolean
        get() = state.isTelemetryEnabled
        set(value) { state.isTelemetryEnabled = value }

    var isHoverEnabled: Boolean
        get() = state.isHoverEnabled
        set(value) { state.isHoverEnabled = value }

    var isCompletionEnabled: Boolean
        get() = state.isCompletionEnabled
        set(value) { state.isCompletionEnabled = value }

    var maxCompletions: Int
        get() = state.maxCompletions
        set(value) { state.maxCompletions = value }

    // CFN-Lint settings
    var isCfnLintEnabled: Boolean
        get() = state.isCfnLintEnabled
        set(value) { state.isCfnLintEnabled = value }

    var cfnLintLintOnChange: Boolean
        get() = state.cfnLintLintOnChange
        set(value) { state.cfnLintLintOnChange = value }

    var cfnLintDelayMs: Int
        get() = state.cfnLintDelayMs
        set(value) { state.cfnLintDelayMs = value }

    var cfnLintPath: String
        get() = state.cfnLintPath
        set(value) { state.cfnLintPath = value }

    var cfnLintIgnoreChecks: String
        get() = state.cfnLintIgnoreChecks
        set(value) { state.cfnLintIgnoreChecks = value }

    var cfnLintIncludeChecks: String
        get() = state.cfnLintIncludeChecks
        set(value) { state.cfnLintIncludeChecks = value }

    var cfnLintIncludeExperimental: Boolean
        get() = state.cfnLintIncludeExperimental
        set(value) { state.cfnLintIncludeExperimental = value }

    var cfnLintCustomRules: String
        get() = state.cfnLintCustomRules
        set(value) { state.cfnLintCustomRules = value }

    var cfnLintAppendRules: String
        get() = state.cfnLintAppendRules
        set(value) { state.cfnLintAppendRules = value }

    var cfnLintOverrideSpec: String
        get() = state.cfnLintOverrideSpec
        set(value) { state.cfnLintOverrideSpec = value }

    var cfnLintRegistrySchemas: String
        get() = state.cfnLintRegistrySchemas
        set(value) { state.cfnLintRegistrySchemas = value }

    // CFN-Guard settings
    var isCfnGuardEnabled: Boolean
        get() = state.isCfnGuardEnabled
        set(value) { state.isCfnGuardEnabled = value }

    var cfnGuardValidateOnChange: Boolean
        get() = state.cfnGuardValidateOnChange
        set(value) { state.cfnGuardValidateOnChange = value }

    var cfnGuardEnabledRulePacks: String
        get() = state.cfnGuardEnabledRulePacks
        set(value) { state.cfnGuardEnabledRulePacks = value }

    var cfnGuardRulesFile: String
        get() = state.cfnGuardRulesFile
        set(value) { state.cfnGuardRulesFile = value }

    data class State(
        var nodeRuntimePath: String = "",
        var isTelemetryEnabled: Boolean = false,
        var isHoverEnabled: Boolean = true,
        var isCompletionEnabled: Boolean = true,
        var maxCompletions: Int = 100,
        // CFN-Lint
        var isCfnLintEnabled: Boolean = true,
        var cfnLintLintOnChange: Boolean = true,
        var cfnLintDelayMs: Int = 3000,
        var cfnLintPath: String = "",
        var cfnLintIgnoreChecks: String = "",
        var cfnLintIncludeChecks: String = "I",
        var cfnLintIncludeExperimental: Boolean = false,
        var cfnLintCustomRules: String = "",
        var cfnLintAppendRules: String = "",
        var cfnLintOverrideSpec: String = "",
        var cfnLintRegistrySchemas: String = "",
        // CFN-Guard
        var isCfnGuardEnabled: Boolean = true,
        var cfnGuardValidateOnChange: Boolean = true,
        var cfnGuardEnabledRulePacks: String = "wa-Security-Pillar",
        var cfnGuardRulesFile: String = ""
    )

    companion object {
        fun getInstance(): CfnLspSettings = service()

        val GUARD_RULE_PACKS = listOf(
            "ABS-CCIGv2-Material",
            "ABS-CCIGv2-Standard",
            "acsc-essential-8",
            "acsc-ism",
            "apra-cpg-234",
            "bnm-rmit",
            "cis-aws-benchmark-level-1",
            "cis-aws-benchmark-level-2",
            "cis-critical-security-controls-v8-ig1",
            "cis-critical-security-controls-v8-ig2",
            "cis-critical-security-controls-v8-ig3",
            "cis-top-20",
            "cisa-ce",
            "cmmc-level-1",
            "cmmc-level-2",
            "cmmc-level-3",
            "cmmc-level-4",
            "cmmc-level-5",
            "enisa-cybersecurity-guide-for-smes",
            "ens-high",
            "ens-low",
            "ens-medium",
            "FDA-21CFR-Part-11",
            "FedRAMP-Low",
            "FedRAMP-Moderate",
            "ffiec",
            "hipaa-security",
            "K-ISMS",
            "mas-notice-655",
            "mas-trmg",
            "nbc-trmg",
            "ncsc-cafv3",
            "ncsc",
            "nerc",
            "nist-1800-25",
            "nist-800-171",
            "nist-800-172",
            "nist-800-181",
            "nist-csf",
            "nist-privacy-framework",
            "NIST800-53Rev4",
            "NIST800-53Rev5",
            "nzism",
            "PCI-DSS-3-2-1",
            "rbi-bcsf-ucb",
            "rbi-md-itf",
            "us-nydfs",
            "wa-Reliability-Pillar",
            "wa-Security-Pillar"
        )
    }
}
