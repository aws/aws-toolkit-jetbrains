// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import kotlinx.coroutines.CoroutineScope
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class DeleteResourceDialog(
    project: Project,
    private val resourceType: String,
    private val resourceName: String
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("DeleteResourceDialog") {
    private val deleteResourceConfirmation = JBTextField()
    private val warningIconForDeletingResource = JBLabel(Messages.getWarningIcon())
    private val component by lazy {
        panel {
            row {
                warningIconForDeletingResource(grow)
                right { label(message("delete_resource.message", resourceType, resourceName)) }
            }
            row {
                deleteResourceConfirmation(grow)
            }
        }
    }

    init {
        super.init()
        title = message("delete_resource.title", resourceType, resourceName)
        deleteResourceConfirmation.emptyText.text = message("delete_resource.confirmation_text")
    }

    override fun createCenterPanel(): JComponent? = component

    override fun doValidate(): ValidationInfo? = validateDeleteConfirmation(deleteResourceConfirmation)

    override fun doOKAction() {
        if (doValidateAll().isNotEmpty()) return
        if (!okAction.isEnabled) {
            return
        }
        close(OK_EXIT_CODE)
    }

    private fun validateDeleteConfirmation(deleteResourceConfirmation: JBTextField): ValidationInfo? {
        if (deleteResourceConfirmation.text != message("delete_resource.confirmation_text")) {
            return ValidationInfo(message("delete_resource.valid_entry_check"), deleteResourceConfirmation)
        }
        return null
    }
}
