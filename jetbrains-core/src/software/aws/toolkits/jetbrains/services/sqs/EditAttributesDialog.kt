// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class EditAttributesDialog(
    private val project: Project,
    private val client: SqsClient,
    private val queue: Queue
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("EditAttributesDialog") {
    val view = EditAttributesPanel(client, queue)

    init {
        title = message("sqs.edit.attributes")
        setOKButtonText(message("sqs.edit.attributes.save"))
        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.visibilityTimeout

    override fun doValidate(): ValidationInfo? {
        if (view.visibilityTimeout.text.isEmpty()) {
            return ValidationInfo(message("sqs.edit.attributes.validation.visibility"), view.visibilityTimeout)
        }
        // TODO: Finish validation
        return null
    }
}
