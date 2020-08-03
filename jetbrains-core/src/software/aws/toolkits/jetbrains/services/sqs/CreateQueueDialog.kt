// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.resources.message
import java.awt.Component
import javax.swing.JComponent

class CreateQueueDialog(
    private val project: Project,
    private val client: SqsClient,
    parent: Component? = null
) : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {
    val view = CreateQueuePanel()

    init {
        super.init()
        title = message("sqs.create.queue.title")
        setOKButtonText(message("sqs.create.queue.create"))
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.queueName

    override fun doValidate(): ValidationInfo? = validateFields()

    // TODO: Override cancel action when telemetry added

    override fun doOKAction() {
        println("OK")
    }

    private fun validateFields(): ValidationInfo? {
        if (view.queueName.text.isEmpty()) {
            return ValidationInfo("Please enter a queue name", view.queueName)
        }

        if (view.fifoType.isSelected) {
            if (!view.queueName.text.endsWith(".fifo")) {
                return ValidationInfo("FIFO queue must end with '.fifo'", view.queueName)
            }
        }

        if (view.standardType.isSelected) {
            if (view.queueName.text.endsWith(".fifo")) {
                return ValidationInfo("Standard queue cannot end with '.fifo'", view.queueName)
            }
        }

        return null
    }
}
