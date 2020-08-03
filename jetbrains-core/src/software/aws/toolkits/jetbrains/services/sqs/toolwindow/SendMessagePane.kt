// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.ui.validationInfo
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class SendMessagePane(
    private val client: SqsClient,
    private val queue: Queue
) : CoroutineScope by ApplicationThreadPoolScope("SendMessagePane") {
    lateinit var component: JPanel
    lateinit var inputText: JBTextArea
    lateinit var fifoComponent: JPanel
    lateinit var deduplicationID: JBTextField
    lateinit var groupID: JBTextField
    lateinit var sendButton: JButton
    lateinit var clearButton: JButton
    lateinit var emptyBodyLabel: JLabel
    lateinit var emptyDeduplicationLabel: JLabel
    lateinit var emptyGroupLabel: JLabel
    lateinit var successPanel: NonOpaquePanel
    var isFifo = true
    var statusLabel = JLabel()
    private val cache = MessageCache.getInstance()

    init {
        setUp()
        sendButton.addActionListener {
            launch { sendMessage() }
        }
        clearButton.addActionListener {
            inputText.text = ""
            deduplicationID.text = ""
            groupID.text = ""
            cache.setMessage(queue.queueUrl, inputText.text)
        }
    }

    private fun setUp() {
        if (!queue.queueName.endsWith(".fifo")) {
            fifoComponent.isVisible = false
            isFifo = false
        }
        emptyDeduplicationLabel.isVisible = false
        emptyGroupLabel.isVisible = false
        emptyBodyLabel.isVisible = false

        inputText.apply {
            emptyText.text = "Enter message body"
        }
        deduplicationID.apply {
            emptyText.text = "(Required)"
        }
        groupID.apply {
            emptyText.text = "(Required)"
        }
        successPanel.apply {
            isVisible = false
            setContent(statusLabel)
        }

        inputText.text = cache.getMessage(queue.queueUrl)
    }

    suspend fun sendMessage() {
        if (validateFields()) {
            try {
                withContext(Dispatchers.IO) {
                    client.sendMessage {
                        it.queueUrl(queue.queueUrl)
                        it.messageBody(inputText.text)
                        if (isFifo) {
                            it.messageDeduplicationId(deduplicationID.text)
                            it.messageGroupId(groupID.text)
                        }
                    }
                }
                // TODO: Display success of message sent
                showSuccess(true)
                println("SUCCESS")
                cache.setMessage(queue.queueUrl, inputText.text)
            } catch (e: Exception) {
                showSuccess(false)
            }
        }
    }

    private fun validateFields(): Boolean {
        emptyBodyLabel.isVisible = inputText.text.isEmpty()
        if (isFifo) {
            emptyDeduplicationLabel.isVisible = deduplicationID.text.isEmpty()
            emptyGroupLabel.isVisible = groupID.text.isEmpty()
        }

        return (!emptyBodyLabel.isVisible && !emptyDeduplicationLabel.isVisible && !emptyGroupLabel.isVisible)
    }

    private suspend fun showSuccess(sent: Boolean) {
        if (sent) {
            statusLabel.text = "Message sent"
        } else {
            statusLabel.text = "Error sending message"
        }

        launch {
            successPanel.isVisible = true
            delay(2000)
            successPanel.isVisible = false
        }
    }
}
