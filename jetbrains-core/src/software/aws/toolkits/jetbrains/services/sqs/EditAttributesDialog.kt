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
import software.amazon.awssdk.services.sqs.model.SqsException
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.SqsTelemetry
import javax.swing.JComponent

class EditAttributesDialog(
    private val project: Project,
    private val client: SqsClient,
    private val queue: Queue,
    private val attributes: Map<QueueAttributeName, String>
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
            view.messageSize.validateContent()
            view.retentionPeriod.validateContent()
        } catch (e: ConfigurationException) {
            return ValidationInfo(e.title)
        }
        return view.waitTime.validate() ?: view.deliveryDelay.validate() ?: view.waitTime.validate()
    }

    override fun doCancelAction() {
        SqsTelemetry.editQueueAttributes(project, Result.Cancelled, queue.telemetryType())
        super.doCancelAction()
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
                    project = project,
                    title = message("sqs.service_name"),
                    content = message("sqs.edit.attributes.updated", queue.queueName)
                )
                SqsTelemetry.editQueueAttributes(project, Result.Succeeded, queue.telemetryType())
                withContext(getCoroutineUiContext(ModalityState.any())) {
                    close(OK_EXIT_CODE)
                }
            } catch (e: SqsException) {
                LOG.error(e) { "Updating queue attributes failed" }
                setErrorText(e.message)
                isOKActionEnabled = true
                SqsTelemetry.editQueueAttributes(project, Result.Failed, queue.telemetryType())
            }
        }
    }

    private fun populateFields() {
        view.visibilityTimeout.value = attributes[QueueAttributeName.VISIBILITY_TIMEOUT]?.toIntOrNull() ?: MIN_VISIBILITY_TIMEOUT
        view.messageSize.text = attributes[QueueAttributeName.MAXIMUM_MESSAGE_SIZE]
        view.retentionPeriod.text = attributes[QueueAttributeName.MESSAGE_RETENTION_PERIOD]
        view.deliveryDelay.value = attributes[QueueAttributeName.DELAY_SECONDS]?.toIntOrNull() ?: MIN_DELIVERY_DELAY
        view.waitTime.value = attributes[QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS]?.toIntOrNull() ?: MIN_WAIT_TIME
    }

    internal fun updateAttributes() {
        client.setQueueAttributes {
            it.queueUrl(queue.queueUrl)
            it.attributes(
                mutableMapOf(
                    Pair(QueueAttributeName.VISIBILITY_TIMEOUT, view.visibilityTimeout.value.toString()),
                    Pair(QueueAttributeName.MAXIMUM_MESSAGE_SIZE, view.messageSize.text),
                    Pair(QueueAttributeName.MESSAGE_RETENTION_PERIOD, view.retentionPeriod.text),
                    Pair(QueueAttributeName.DELAY_SECONDS, view.deliveryDelay.value.toString()),
                    Pair(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, view.waitTime.value.toString())
                )
            )
        }
    }

    private companion object {
        val LOG = getLogger<EditAttributesDialog>()
    }
}
