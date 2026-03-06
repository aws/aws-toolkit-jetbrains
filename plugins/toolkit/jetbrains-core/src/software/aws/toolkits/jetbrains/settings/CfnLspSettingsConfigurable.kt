// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class CfnLspSettingsConfigurable : BoundConfigurable(message("cloudformation.settings.title")), SearchableConfigurable {
    private val settings = CfnLspSettings.getInstance()

    override fun createPanel() = panel {
        group(message("cloudformation.settings.general.group")) {
            row(message("cloudformation.settings.node.path")) {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(message("cloudformation.settings.node.path.browse"))
                ).bindText(settings::nodeRuntimePath).columns(30).comment(message("cloudformation.settings.node.path.comment"))
            }
            row {
                checkBox(message("cloudformation.settings.telemetry.enable")).bindSelected(settings::isTelemetryEnabled)
            }
        }

        group(message("cloudformation.settings.hover.group")) {
            row {
                checkBox(message("cloudformation.settings.hover.enable")).bindSelected(settings::isHoverEnabled)
            }
        }

        group(message("cloudformation.settings.completion.group")) {
            row {
                checkBox(message("cloudformation.settings.completion.enable")).bindSelected(settings::isCompletionEnabled)
            }
            row(message("cloudformation.settings.completion.max")) {
                intTextField(1..1000).bindIntText(settings::maxCompletions).columns(6)
            }
        }

        collapsibleGroup(message("cloudformation.settings.cfnlint.group")) {
            row {
                checkBox(message("cloudformation.settings.cfnlint.enable")).bindSelected(settings::isCfnLintEnabled)
            }
            row {
                checkBox(message("cloudformation.settings.cfnlint.lintOnChange")).bindSelected(settings::cfnLintLintOnChange)
            }
            row(message("cloudformation.settings.cfnlint.delayMs")) {
                intTextField(0..60000).bindIntText(settings::cfnLintDelayMs).columns(6).comment(message("cloudformation.settings.cfnlint.delayMs.comment"))
            }
            row(message("cloudformation.settings.cfnlint.path")) {
                textField().bindText(settings::cfnLintPath).columns(30).comment(message("cloudformation.settings.cfnlint.path.comment"))
            }
            row {
                checkBox(message("cloudformation.settings.cfnlint.includeExperimental")).bindSelected(settings::cfnLintIncludeExperimental)
            }
            row(message("cloudformation.settings.cfnlint.ignoreChecks")) {
                textField().bindText(settings::cfnLintIgnoreChecks).columns(30).comment(message("cloudformation.settings.cfnlint.ignoreChecks.comment"))
            }
            row(message("cloudformation.settings.cfnlint.includeChecks")) {
                textField().bindText(settings::cfnLintIncludeChecks).columns(30).comment(message("cloudformation.settings.cfnlint.includeChecks.comment"))
            }
            row(message("cloudformation.settings.cfnlint.customRules")) {
                textField().bindText(settings::cfnLintCustomRules).columns(30).comment(message("cloudformation.settings.cfnlint.customRules.comment"))
            }
            row(message("cloudformation.settings.cfnlint.appendRules")) {
                textField().bindText(settings::cfnLintAppendRules).columns(30).comment(message("cloudformation.settings.cfnlint.appendRules.comment"))
            }
            row(message("cloudformation.settings.cfnlint.overrideSpec")) {
                textField().bindText(settings::cfnLintOverrideSpec).columns(30).comment(message("cloudformation.settings.cfnlint.overrideSpec.comment"))
            }
            row(message("cloudformation.settings.cfnlint.registrySchemas")) {
                textField().bindText(settings::cfnLintRegistrySchemas).columns(30).comment(message("cloudformation.settings.cfnlint.registrySchemas.comment"))
            }
        }

        collapsibleGroup(message("cloudformation.settings.cfnguard.group")) {
            row {
                checkBox(message("cloudformation.settings.cfnguard.enable")).bindSelected(settings::isCfnGuardEnabled)
            }
            row {
                checkBox(message("cloudformation.settings.cfnguard.validateOnChange")).bindSelected(settings::cfnGuardValidateOnChange)
            }
            row(message("cloudformation.settings.cfnguard.enabledRulePacks")) {
                val selected = settings.cfnGuardEnabledRulePacks.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                val checkBoxList = CheckBoxList<String>().apply {
                    CFN_GUARD_RULE_PACKS.forEach { addItem(it, it, it in selected) }
                }

                cell(
                    JBScrollPane(checkBoxList).apply {
                        preferredSize = JBUI.size(300, 150)
                    }
                ).onApply {
                    settings.cfnGuardEnabledRulePacks = CFN_GUARD_RULE_PACKS.filter { checkBoxList.isItemSelected(it) }.joinToString(",")
                }.onReset {
                    val current = settings.cfnGuardEnabledRulePacks.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    CFN_GUARD_RULE_PACKS.forEachIndexed { _, pack ->
                        checkBoxList.setItemSelected(pack, pack in current)
                    }
                }.onIsModified {
                    val current = settings.cfnGuardEnabledRulePacks.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    val ui = CFN_GUARD_RULE_PACKS.filter { checkBoxList.isItemSelected(it) }.toSet()
                    current != ui
                }
            }
            row(message("cloudformation.settings.cfnguard.rulesFile")) {
                textFieldWithBrowseButton(
                    fileChooserDescriptor = FileChooserDescriptorFactory.singleFile()
                ).bindText(settings::cfnGuardRulesFile).columns(30).comment(message("cloudformation.settings.cfnguard.rulesFile.comment"))
            }
        }
    }

    override fun apply() {
        super.apply()
        settings.notifySettingsChanged()
    }

    override fun getId(): String = "aws.cloudformation"
}

private val CFN_GUARD_RULE_PACKS = listOf(
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
    "wa-Security-Pillar",
)
