// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import software.aws.toolkits.resources.AwsCoreBundle

class CfnSettingsConfigurable : BoundConfigurable(AwsCoreBundle.message("cloudformation.settings.title")), SearchableConfigurable {
    private val settings = CfnSettings.getInstance()

    override fun createPanel() = panel {
        group(AwsCoreBundle.message("cloudformation.settings.general.group")) {
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.lsp.enable"))
                    .bindSelected(settings::isLspEnabled)
                    .comment(AwsCoreBundle.message("cloudformation.settings.lsp.enable.comment"))
            }
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.telemetry.enable"))
                    .bindSelected(settings::isTelemetryEnabled)
            }
        }

        group(AwsCoreBundle.message("cloudformation.settings.hover.group")) {
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.hover.enable"))
                    .bindSelected(settings::isHoverEnabled)
            }
        }

        group(AwsCoreBundle.message("cloudformation.settings.completion.group")) {
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.completion.enable"))
                    .bindSelected(settings::isCompletionEnabled)
            }
            row(AwsCoreBundle.message("cloudformation.settings.completion.max")) {
                intTextField(1..1000)
                    .bindIntText(settings::maxCompletions)
                    .columns(6)
            }
        }

        collapsibleGroup(AwsCoreBundle.message("cloudformation.settings.cfnlint.group")) {
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.cfnlint.enable"))
                    .bindSelected(settings::isCfnLintEnabled)
            }
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.cfnlint.lintOnChange"))
                    .bindSelected(settings::cfnLintLintOnChange)
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.delayMs")) {
                intTextField(0..60000)
                    .bindIntText(settings::cfnLintDelayMs)
                    .columns(6)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.delayMs.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.path")) {
                textField()
                    .bindText(settings::cfnLintPath)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.path.comment"))
            }
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.cfnlint.includeExperimental"))
                    .bindSelected(settings::cfnLintIncludeExperimental)
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.ignoreChecks")) {
                textField()
                    .bindText(settings::cfnLintIgnoreChecks)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.ignoreChecks.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.includeChecks")) {
                textField()
                    .bindText(settings::cfnLintIncludeChecks)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.includeChecks.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.customRules")) {
                textField()
                    .bindText(settings::cfnLintCustomRules)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.customRules.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.appendRules")) {
                textField()
                    .bindText(settings::cfnLintAppendRules)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.appendRules.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.overrideSpec")) {
                textField()
                    .bindText(settings::cfnLintOverrideSpec)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.overrideSpec.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnlint.registrySchemas")) {
                textField()
                    .bindText(settings::cfnLintRegistrySchemas)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnlint.registrySchemas.comment"))
            }
        }

        collapsibleGroup(AwsCoreBundle.message("cloudformation.settings.cfnguard.group")) {
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.cfnguard.enable"))
                    .bindSelected(settings::isCfnGuardEnabled)
            }
            row {
                checkBox(AwsCoreBundle.message("cloudformation.settings.cfnguard.validateOnChange"))
                    .bindSelected(settings::cfnGuardValidateOnChange)
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnguard.enabledRulePacks")) {
                textField()
                    .bindText(settings::cfnGuardEnabledRulePacks)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnguard.enabledRulePacks.comment"))
            }
            row(AwsCoreBundle.message("cloudformation.settings.cfnguard.rulesFile")) {
                textField()
                    .bindText(settings::cfnGuardRulesFile)
                    .columns(30)
                    .comment(AwsCoreBundle.message("cloudformation.settings.cfnguard.rulesFile.comment"))
            }
        }
    }

    override fun getId(): String = "aws.cloudformation"
}
