// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import software.aws.toolkits.resources.AwsToolkitBundle.message

internal class CfnLspSettingsConfigurable : BoundConfigurable(message("cloudformation.settings.title")), SearchableConfigurable {
    private val settings = CfnLspSettings.getInstance()

    override fun createPanel() = panel {
        group(message("cloudformation.settings.general.group")) {
            row(message("cloudformation.settings.node.path")) {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                        .withTitle(message("cloudformation.settings.node.path.browse"))
                )
                    .bindText(settings::nodeRuntimePath)
                    .columns(30)
                    .comment(message("cloudformation.settings.node.path.comment"))
            }
            row {
                checkBox(message("cloudformation.settings.telemetry.enable"))
                    .bindSelected(settings::isTelemetryEnabled)
            }
        }

        group(message("cloudformation.settings.hover.group")) {
            row {
                checkBox(message("cloudformation.settings.hover.enable"))
                    .bindSelected(settings::isHoverEnabled)
            }
        }

        group(message("cloudformation.settings.completion.group")) {
            row {
                checkBox(message("cloudformation.settings.completion.enable"))
                    .bindSelected(settings::isCompletionEnabled)
            }
            row(message("cloudformation.settings.completion.max")) {
                intTextField(1..1000)
                    .bindIntText(settings::maxCompletions)
                    .columns(6)
            }
        }

        collapsibleGroup(message("cloudformation.settings.cfnlint.group")) {
            row {
                checkBox(message("cloudformation.settings.cfnlint.enable"))
                    .bindSelected(settings::isCfnLintEnabled)
            }
            row {
                checkBox(message("cloudformation.settings.cfnlint.lintOnChange"))
                    .bindSelected(settings::cfnLintLintOnChange)
            }
            row(message("cloudformation.settings.cfnlint.delayMs")) {
                intTextField(0..60000)
                    .bindIntText(settings::cfnLintDelayMs)
                    .columns(6)
                    .comment(message("cloudformation.settings.cfnlint.delayMs.comment"))
            }
            row(message("cloudformation.settings.cfnlint.path")) {
                textField()
                    .bindText(settings::cfnLintPath)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.path.comment"))
            }
            row {
                checkBox(message("cloudformation.settings.cfnlint.includeExperimental"))
                    .bindSelected(settings::cfnLintIncludeExperimental)
            }
            row(message("cloudformation.settings.cfnlint.ignoreChecks")) {
                textField()
                    .bindText(settings::cfnLintIgnoreChecks)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.ignoreChecks.comment"))
            }
            row(message("cloudformation.settings.cfnlint.includeChecks")) {
                textField()
                    .bindText(settings::cfnLintIncludeChecks)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.includeChecks.comment"))
            }
            row(message("cloudformation.settings.cfnlint.customRules")) {
                textField()
                    .bindText(settings::cfnLintCustomRules)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.customRules.comment"))
            }
            row(message("cloudformation.settings.cfnlint.appendRules")) {
                textField()
                    .bindText(settings::cfnLintAppendRules)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.appendRules.comment"))
            }
            row(message("cloudformation.settings.cfnlint.overrideSpec")) {
                textField()
                    .bindText(settings::cfnLintOverrideSpec)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.overrideSpec.comment"))
            }
            row(message("cloudformation.settings.cfnlint.registrySchemas")) {
                textField()
                    .bindText(settings::cfnLintRegistrySchemas)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnlint.registrySchemas.comment"))
            }
        }

        collapsibleGroup(message("cloudformation.settings.cfnguard.group")) {
            row {
                checkBox(message("cloudformation.settings.cfnguard.enable"))
                    .bindSelected(settings::isCfnGuardEnabled)
            }
            row {
                checkBox(message("cloudformation.settings.cfnguard.validateOnChange"))
                    .bindSelected(settings::cfnGuardValidateOnChange)
            }
            row(message("cloudformation.settings.cfnguard.enabledRulePacks")) {
                textField()
                    .bindText(settings::cfnGuardEnabledRulePacks)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnguard.enabledRulePacks.comment"))
            }
            row(message("cloudformation.settings.cfnguard.rulesFile")) {
                textField()
                    .bindText(settings::cfnGuardRulesFile)
                    .columns(30)
                    .comment(message("cloudformation.settings.cfnguard.rulesFile.comment"))
            }
        }
    }

    override fun apply() {
        super.apply()
        settings.notifySettingsChanged()
    }

    override fun getId(): String = "aws.cloudformation"
}
