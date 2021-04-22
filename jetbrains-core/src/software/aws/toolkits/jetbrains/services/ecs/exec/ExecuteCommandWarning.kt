// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.settings.EcsExecCommandSettings
import software.aws.toolkits.jetbrains.utils.ui.visible
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class ExecuteCommandWarning(project: Project, enable: Boolean) : DialogWrapper(project) {
    private val warningIcon = JBLabel(Messages.getWarningIcon())
    private val enableExecuteCommandWarning = JBLabel(message("ecs.execute_command_enable_warning"))
    private val disableExecuteCommandWarning = JBLabel(message("ecs.execute_command_disable_warning"))
    private val dontDisplayWarning = JBCheckBox(message("notice.suppress"))
    private val settings = EcsExecCommandSettings.getInstance()
    private val component by lazy {
        panel {
            row {
                warningIcon(grow)
                right {
                    enableExecuteCommandWarning(grow).visible(enable)
                    disableExecuteCommandWarning(grow).visible(!enable)
                }
            }
            row {
                dontDisplayWarning(grow)
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
        if (dontDisplayWarning.isSelected) {
            settings.showExecuteCommandWarning = false
        }
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent = component
}
