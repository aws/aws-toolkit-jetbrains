// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.explorer.refreshAwsTree
import software.aws.toolkits.jetbrains.services.sqs.resources.SqsResources
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
        title = message("sqs.create.queue.title")
        setOKButtonText(message("sqs.create.queue.create"))

        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun getPreferredFocusedComponent(): JComponent? = view.queueName

    override fun doValidate(): ValidationInfo? = validateFields()

    // TODO: Override cancel action when telemetry added

    override fun doOKAction() {
        if (isOKActionEnabled) {
            setOKButtonText(message("sqs.create.queue.in_progress"))
            isOKActionEnabled = false

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    createQueue()
                    ApplicationManager.getApplication().invokeLater({
                        close(OK_EXIT_CODE)
                    }, ModalityState.stateForComponent(view.component))
                    project.refreshAwsTree(SqsResources.LIST_QUEUE_URLS)
                } catch (e: Exception) {
                    LOG.warn(e) { message("sqs.create.queue.failed", queueName()) }
                    setErrorText(e.message)
                    setOKButtonText(message("sqs.create.queue.create"))
                    isOKActionEnabled = true
                }
            }
        }
    }

    private fun validateFields(): ValidationInfo? {
        if (queueName().isEmpty()) {
            return ValidationInfo(message("sqs.create.validation.empty.queue.name"), view.queueName)
        } else if (queueName().length > MAX_LENGTH_OF_QUEUE_NAME) {
            return ValidationInfo(message("sqs.create.validation.long.queue.name", MAX_LENGTH_OF_QUEUE_NAME), view.queueName)
        }

        if (view.fifoType.isSelected) {
            if (!queueName().endsWith(".fifo")) {
                return ValidationInfo(message("sqs.create.validation.fifo"), view.queueName)
            } else if (!validateCharacters(queueName().substringBefore(".fifo"))) {
                return ValidationInfo(message("sqs.create.validation.queue.name.invalid"), view.queueName)
            }
        } else {
            if (!validateCharacters(queueName())) {
                return ValidationInfo(message("sqs.create.validation.queue.name.invalid"), view.queueName)
            }
        }

        return null
    }

    private fun validateCharacters(queueName: String): Boolean = queueName.matches("^[-a-zA-Z0-9_]*$".toRegex())

    private fun queueName() = view.queueName.text.trim()

    fun createQueue() {
        client.createQueue {
            it.queueName(queueName())
        }
    }

    private companion object {
        val LOG = getLogger<CreateQueueDialog>()
    }
}
