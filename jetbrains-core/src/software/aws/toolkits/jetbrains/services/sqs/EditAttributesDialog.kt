// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
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
        prepopulateFields()
        init()
    }

    override fun createCenterPanel(): JComponent? = view.component

    override fun doValidate(): ValidationInfo? {
        if (view.visibilityTimeout.text.isEmpty()) {
            return ValidationInfo(message("sqs.edit.attributes.validation.visibility.empty"), view.visibilityTimeout)
        }
        if ((visibilityTimeout() < 0) || (visibilityTimeout() > MAX_VISIBILITY_TIMEOUT)) {
            return ValidationInfo(message("sqs.edit.attributes.validation.visibility.bounds"), view.visibilityTimeout)
        }

        if (view.messageSize.text.isEmpty()) {
            return ValidationInfo(message("sqs.edit.attributes.validation.maximum_size.empty"), view.messageSize)
        }
        if ((messageSize() < MIN_MESSAGE_SIZE_LIMIT) || (messageSize() > MAX_MESSAGE_SIZE_LIMIT)) {
            return ValidationInfo(message("sqs.edit.attributes.validation.maximum_size.bounds"), view.messageSize)
        }

        if (view.retentionPeriod.text.isEmpty()) {
            return ValidationInfo(message("sqs.edit.attributes.validation.retention.empty"), view.retentionPeriod)
        }
        if ((retentionPeriod() < MIN_RETENTION_PERIOD) || (retentionPeriod() > MAX_RETENTION_PERIOD)) {
            return ValidationInfo(message("sqs.edit.attributes.validation.retention.bounds"), view.retentionPeriod)
        }

        if (view.deliveryDelay.text.isEmpty()) {
            return ValidationInfo(message("sqs.edit.attributes.validation.delay.empty"), view.deliveryDelay)
        }
        if ((deliveryDelay() < 0) || (deliveryDelay() > MAX_DELIVERY_DELAY)) {
            return ValidationInfo(message("sqs.edit.attributes.validation.delay.bounds"), view.deliveryDelay)
        }

        if (view.waitTime.text.isEmpty()) {
            return ValidationInfo(message("sqs.edit.attributes.validation.wait_time.empty"), view.waitTime)
        }
        if ((waitTime() < 0) || (waitTime() > MAX_WAIT_TIME)) {
            return ValidationInfo(message("sqs.edit.attributes.validation.wait_time.bounds"), view.waitTime)
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
                println("SUCCESS")
                runInEdt(ModalityState.any()) {
                    close(OK_EXIT_CODE)
                }
            } catch (e: Exception) {
                setErrorText(e.message)
                isOKActionEnabled = false
            }
        }
    }

    private fun prepopulateFields() {
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

    private fun updateAttributes() {
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

    private fun visibilityTimeout(): Int = view.visibilityTimeout.text.toInt()
    private fun messageSize(): Int = view.messageSize.text.toInt()
    private fun retentionPeriod(): Int = view.retentionPeriod.text.toInt()
    private fun deliveryDelay(): Int = view.deliveryDelay.text.toInt()
    private fun waitTime(): Int = view.waitTime.text.toInt()
}
