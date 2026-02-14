// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.io.File
import javax.swing.JComponent

internal data class ValidateAndDeploySettings(
    val templatePath: String,
    val stackName: String,
)

internal class ValidateAndDeployDialog(
    project: Project,
    private val prefilledTemplatePath: String? = null,
    private val prefilledStackName: String? = null,
) : DialogWrapper(project) {

    private val descriptor = FileChooserDescriptor(true, false, false, false, false, false).withFileFilter {
        it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
    }

    private val templateField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, descriptor)
        prefilledTemplatePath?.let { text = it }
    }

    private val stackNameField = JBTextField().apply {
        prefilledStackName?.let { text = it }
        emptyText.text = message("cloudformation.deploy.dialog.stack_name.placeholder")
    }

    init {
        title = message("cloudformation.deploy.dialog.title")
        init()
    }

    override fun getDimensionServiceKey(): String = "aws.toolkit.cloudformation.validateAndDeploy"

    override fun createCenterPanel(): JComponent = panel {
        row(message("cloudformation.deploy.dialog.template.label")) {
            cell(templateField).align(Align.FILL)
        }
        row(message("cloudformation.deploy.dialog.stack_name.label")) {
            cell(stackNameField).align(Align.FILL)
        }
    }.apply {
        preferredSize = JBUI.size(450, 120)
    }

    override fun getPreferredFocusedComponent(): JComponent =
        if (prefilledTemplatePath != null) stackNameField else templateField.textField

    override fun doValidate(): ValidationInfo? {
        val path = templateField.text.trim()
        if (path.isBlank()) {
            return ValidationInfo(message("cloudformation.deploy.dialog.template.required"), templateField)
        }
        val file = File(path)
        if (!file.isFile) {
            return ValidationInfo(message("cloudformation.deploy.dialog.template.not_found"), templateField)
        }
        if (file.extension.lowercase() !in CFN_SUPPORTED_EXTENSIONS) {
            return ValidationInfo(message("cloudformation.deploy.dialog.template.invalid_extension"), templateField)
        }
        val name = stackNameField.text.trim()
        if (name.isBlank()) {
            return ValidationInfo(message("cloudformation.deploy.dialog.stack_name.required"), stackNameField)
        }
        if (name.length > 128) {
            return ValidationInfo(message("cloudformation.deploy.dialog.stack_name.too_long"), stackNameField)
        }
        if (!STACK_NAME_PATTERN.matches(name)) {
            return ValidationInfo(message("cloudformation.deploy.dialog.stack_name.invalid"), stackNameField)
        }
        return null
    }

    fun getSettings(): ValidateAndDeploySettings = ValidateAndDeploySettings(
        templatePath = templateField.text.trim(),
        stackName = stackNameField.text.trim(),
    )

    companion object {
        private val STACK_NAME_PATTERN = Regex("^[a-zA-Z][-a-zA-Z0-9]*$")
    }
}
