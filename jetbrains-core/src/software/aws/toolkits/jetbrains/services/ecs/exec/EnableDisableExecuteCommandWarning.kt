// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.settings.EcsExecCommandSettings
import software.aws.toolkits.jetbrains.utils.ui.visible
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class EnableDisableExecuteCommandWarning(project: Project, enable: Boolean, serviceName: String) : DialogWrapper(project) {
    private val warningIcon = JBLabel(Messages.getWarningIcon())
    private var dontDisplayWarning = false
    private var confirmNonProduction = false
    private val settings = EcsExecCommandSettings.getInstance()
    private val component by lazy {
        panel {
            row {
                warningIcon(grow)
                right {
                    label(message("ecs.execute_command_enable_warning")).constraints(grow).visible(enable)
                    label(message("ecs.execute_command_disable_warning")).constraints(grow).visible(!enable)
                }
            }
            row {
                checkBox(
                    message("ecs.execute_command.production_warning.checkbox_label", serviceName),
                    { confirmNonProduction },
                    { confirmNonProduction = it }
                ).withErrorOnApplyIf(message("general.confirm_proceed")) { !it.isSelected }
                    .constraints(grow)
            }
            row {
                checkBox(message("notice.suppress"), { dontDisplayWarning }, { dontDisplayWarning = it })
            }
        }
    }

    init {
        super.init()
        title = if (enable) {
            message("ecs.execute_command_enable_warning_title")
        } else {
            message("ecs.execute_command_disable_warning_title")
        }
    }

    override fun doOKAction() {
        super.doOKAction()
        if (dontDisplayWarning) {
            settings.showExecuteCommandWarning = false
        }
    }

    override fun createCenterPanel(): JComponent = component
}
