// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.datagrip.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class ConfirmCredentialsDialogWrapper(project: Project, private val errorMessage: ValidationInfo) : DialogWrapper(project) {
    init {
        title = message("datagrip.secretsmanager.action.confirm_continue_title")
        init()
    }

    override fun createCenterPanel(): JComponent? = JBLabel(message("datagrip.secretsmanager.action.confirm_continue", errorMessage.message)).also {
        it.icon = Messages.getWarningIcon()
        it.iconTextGap = 8
    }
}
