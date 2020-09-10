// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class EditAttributesDialog(
    project: Project,
    private val client: SqsClient,
    private val queue: Queue
) : DialogWrapper(project), CoroutineScope by ApplicationThreadPoolScope("EditAttributesDialog") {
    val view = EditAttributesPanel()

    init {
        title = message("sqs.edit.attributes")
        setOKButtonText(message("sqs.edit.attributes.save"))
        populateFields()
        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun doValidate(): ValidationInfo? {
        try {
            view.deliveryDelay.validateContent()
            view.visibilityTimeout.validateContent()
            view.messageSize.validateContent()
            view.waitTime.validateContent()
            view.retentionPeriod.validateContent()
        } catch (e: ConfigurationException) {
            return ValidationInfo(e.title)
        }
        return null
    }

    override fun doOKAction() {
        if (!isOKActionEnabled) {
            return
        }
        isOKActionEnabled = false
        launch {
            try {
                updateAttributes()
                notifyInfo(
                    title = message("sqs.service_name"),
                    content = message("sqs.edit.attributes.updated", queue.queueName)
                )
                withContext(getCoroutineUiContext(ModalityState.any())) {
                    close(OK_EXIT_CODE)
                }
            } catch (e: Exception) {
                setErrorText(e.message)
                isOKActionEnabled = false
            }
        }
    }

    private fun populateFields() {
        launch {
            val currentAttributes = client.getQueueAttributes {
                it.queueUrl(queue.queueUrl)
                it.attributeNames(QueueAttributeName.ALL)
            }.attributes()
            view.visibilityTimeout.text = currentAttributes[QueueAttributeName.VISIBILITY_TIMEOUT]
            view.messageSize.text = currentAttributes[QueueAttributeName.MAXIMUM_MESSAGE_SIZE]
            view.retentionPeriod.text = currentAttributes[QueueAttributeName.MESSAGE_RETENTION_PERIOD]
            view.deliveryDelay.text = currentAttributes[QueueAttributeName.DELAY_SECONDS]
            view.waitTime.text = currentAttributes[QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS]
        }
    }

    internal fun updateAttributes() {
        val attributes = mutableMapOf(
            Pair(QueueAttributeName.VISIBILITY_TIMEOUT, view.visibilityTimeout.text),
            Pair(QueueAttributeName.MAXIMUM_MESSAGE_SIZE, view.messageSize.text),
            Pair(QueueAttributeName.MESSAGE_RETENTION_PERIOD, view.retentionPeriod.text),
            Pair(QueueAttributeName.DELAY_SECONDS, view.deliveryDelay.text),
            Pair(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, view.waitTime.text)
        )
        client.setQueueAttributes {
            it.queueUrl(queue.queueUrl)
            it.attributes(attributes)
        }
    }
}
