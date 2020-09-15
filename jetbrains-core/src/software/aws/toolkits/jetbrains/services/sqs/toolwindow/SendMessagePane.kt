// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.ide.plugins.newui.UpdateButton
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.services.sqs.telemetryType
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.SqsTelemetry
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class SendMessagePane(
    private val project: Project,
    private val client: SqsClient,
    private val queue: Queue
) : CoroutineScope by ApplicationThreadPoolScope("SendMessagePane") {
    lateinit var component: JPanel
    lateinit var inputText: JBTextArea
    lateinit var sendButton: UpdateButton
    lateinit var clearButton: JButton
    lateinit var messageSentLabel: JLabel
    lateinit var fifoFields: FifoPanel
    lateinit var scrollPane: JScrollPane
    private val edt = getCoroutineUiContext()

    init {
        loadComponents()
        setButtons()
        setFields()
    }

    private fun loadComponents() {
        if (!queue.isFifo) {
            fifoFields.component.isVisible = false
        }
        messageSentLabel.isVisible = false
    }

    private fun setButtons() {
        sendButton.addActionListener {
            launch { sendMessage() }
        }
        clearButton.addActionListener {
            runBlocking { clear() }
            messageSentLabel.isVisible = false
        }
    }

    private fun setFields() {
        scrollPane.apply {
            border = IdeBorderFactory.createBorder()
        }
        inputText.apply {
            emptyText.text = message("sqs.send.message.body.empty.text")
        }
    }

    suspend fun sendMessage() {
        if (!validateFields()) {
            return
        }
        try {
            withContext(Dispatchers.IO) {
                val messageId = client.sendMessage {
                    it.queueUrl(queue.queueUrl)
                    it.messageBody(inputText.text)
                    if (queue.isFifo) {
                        it.messageDeduplicationId(fifoFields.deduplicationId.text)
                        it.messageGroupId(fifoFields.groupId.text)
                    }
                }.messageId()
                messageSentLabel.text = message("sqs.send.message.success", messageId)
            }
            clear(isSend = true)
            SqsTelemetry.sendMessage(project, Result.Succeeded, queue.telemetryType())
        } catch (e: Exception) {
            messageSentLabel.text = message("sqs.failed_to_send_message")
            SqsTelemetry.sendMessage(project, Result.Failed, queue.telemetryType())
            clear(isSend = true)
        } finally {
            messageSentLabel.isVisible = true
        }
    }

    suspend fun validateFields(): Boolean {
        val validationIssues = mutableListOf<ValidationInfo>().apply {
            if (inputText.text.isEmpty()) {
                add(ValidationInfo(message("sqs.message.validation.empty.message.body"), inputText))
            }
            if (queue.isFifo) {
                addAll(fifoFields.validateFields())
            }
        }
        return if (validationIssues.isEmpty()) {
            true
        } else {
            withContext(getCoroutineUiContext(ModalityState.any())) {
                validationIssues.forEach { validationIssue ->
                    val errorComponent = validationIssue.component ?: inputText
                    ComponentValidator
                        .createPopupBuilder(validationIssue, null)
                        .setCancelOnClickOutside(true)
                        .createPopup()
                        .showUnderneathOf(errorComponent)
                }
            }
            false
        }
    }

    suspend fun clear(isSend: Boolean = false) = withContext(edt) {
        inputText.text = ""
        if (queue.isFifo) {
            fifoFields.clear(isSend)
        }
    }
}
