// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import software.aws.toolkits.jetbrains.services.cfnlsp.documents.CfnDocumentManager
import software.aws.toolkits.jetbrains.services.cfnlsp.documents.RelativePathParser
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent

internal data class TemplateItem(
    val displayName: String,
    val uri: String?,
) {
    override fun toString() = displayName
}

internal data class ValidateAndDeploySettings(
    val templatePath: String,
    val stackName: String,
)

internal class ValidateAndDeployDialog(
    private val project: Project,
    private val documentManager: CfnDocumentManager,
    private val prefilledTemplatePath: String? = null,
    private val prefilledStackName: String? = null,
) : DialogWrapper(project) {

    private val descriptor = FileChooserDescriptor(true, false, false, false, false, false).withFileFilter {
        it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
    }

    private val templateDropdown = JComboBox<TemplateItem>()

    private val browseButton = JButton().apply {
        icon = AllIcons.Actions.NewFolder
        addActionListener {
            val selectedFile = FileChooser.chooseFile(descriptor, project, null)
            selectedFile?.let { addFileToDropdown(it.path) }
        }
        toolTipText = "Browse for CloudFormation template"
    }

    private val stackNameField = JBTextField().apply {
        prefilledStackName?.let { text = it }
        emptyText.text = message("cloudformation.deploy.dialog.stack_name.placeholder")
    }

    init {
        title = message("cloudformation.deploy.dialog.title")
        populateTemplateDropdown()
        prefilledTemplatePath?.let { addFileToDropdown(it) }
        init()
    }

    private fun populateTemplateDropdown() {
        val templates = documentManager.getValidTemplates()
        templateDropdown.removeAllItems()

        if (templates.isEmpty()) {
            templateDropdown.addItem(TemplateItem("No templates detected, browse with folder icon", null))
        } else {
            templates.sortedBy { it.fileName }.forEach { template ->
                val relativePath = RelativePathParser.getRelativePath(template.uri, project)
                val displayName = "${template.fileName} ($relativePath)"
                templateDropdown.addItem(TemplateItem(displayName, template.uri))
            }
        }
    }

    private fun addFileToDropdown(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return

        val fileUri = "file://$filePath"

        // Check if already exists in dropdown
        val existingItem = (0 until templateDropdown.itemCount)
            .map { templateDropdown.getItemAt(it) }
            .find { it.uri == fileUri }

        if (existingItem != null) {
            templateDropdown.selectedItem = existingItem
        } else {
            // Add new item and select it
            val relativePath = RelativePathParser.getRelativePath(fileUri, project)
            val displayName = "${file.name} ($relativePath)"
            val newItem = TemplateItem(displayName, fileUri)

            templateDropdown.addItem(newItem)
            templateDropdown.selectedItem = newItem
        }
    }

    override fun getDimensionServiceKey(): String = "aws.toolkit.cloudformation.validateAndDeploy"

    override fun createCenterPanel(): JComponent = panel {
        row(message("cloudformation.deploy.dialog.template.label")) {
            cell(templateDropdown).align(Align.FILL)
            cell(browseButton)
        }
        row(message("cloudformation.deploy.dialog.stack_name.label")) {
            cell(stackNameField).align(Align.FILL)
        }
    }.apply {
        preferredSize = JBUI.size(450, 120)
    }

    override fun getPreferredFocusedComponent(): JComponent =
        if (prefilledTemplatePath != null) stackNameField else templateDropdown

    override fun doValidate(): ValidationInfo? {
        val selectedItem = templateDropdown.selectedItem as? TemplateItem
        val uri = selectedItem?.uri

        if (uri == null) {
            return ValidationInfo(message("cloudformation.deploy.dialog.template.required"), templateDropdown)
        }

        val file = File(uri.removePrefix("file://"))
        if (!file.isFile) {
            return ValidationInfo(message("cloudformation.deploy.dialog.template.not_found"), templateDropdown)
        }
        if (file.extension.lowercase() !in CFN_SUPPORTED_EXTENSIONS) {
            return ValidationInfo(message("cloudformation.deploy.dialog.template.invalid_extension"), templateDropdown)
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

    fun getSettings(): ValidateAndDeploySettings {
        val selectedItem = templateDropdown.selectedItem as? TemplateItem
        val templatePath = selectedItem?.uri?.removePrefix("file://") ?: ""

        return ValidateAndDeploySettings(
            templatePath = templatePath,
            stackName = stackNameField.text.trim(),
        )
    }

    companion object {
        private val STACK_NAME_PATTERN = Regex("^[a-zA-Z][-a-zA-Z0-9]*$")
    }
}
