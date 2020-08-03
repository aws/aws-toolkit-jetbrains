// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
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
    lateinit var deduplicationId: JBTextField
    lateinit var groupId: JBTextField
    lateinit var sendButton: JButton
    lateinit var clearButton: JButton
    lateinit var emptyBodyLabel: JLabel
    lateinit var emptyDeduplicationLabel: JLabel
    lateinit var emptyGroupLabel: JLabel
    lateinit var confirmationPanel: NonOpaquePanel
    lateinit var deduplicationContextHelp: JLabel
    lateinit var groupContextHelp: JLabel
    var statusLabel = JLabel()
    private var isFifo = true
    private val cache = MessageCache.getInstance()

    init {
        setUp()
        sendButton.addActionListener {
            launch { sendMessage() }
        }
        clearButton.addActionListener {
            inputText.text = ""
            deduplicationId.text = ""
            groupId.text = ""
            cache.setMessage(queue.queueUrl, inputText.text)
            confirmationPanel.isVisible = false
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
        confirmationPanel.apply {
            isVisible = false
            setContent(statusLabel)
        }

        deduplicationContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.message.deduplication_id.tooltip"))
            installOn(deduplicationContextHelp)
        }
        groupContextHelp.icon = AllIcons.General.ContextHelp
        HelpTooltip().apply {
            setDescription(message("sqs.message.group_id.tooltip"))
            installOn(groupContextHelp)
        }

        inputText.emptyText.text = message("sqs.send.message.body.empty.text")
        deduplicationId.emptyText.text = message("sqs.required.empty.text")
        groupId.emptyText.text = message("sqs.required.empty.text")

        inputText.text = cache.getMessage(queue.queueUrl)
    }

    suspend fun sendMessage() {
        if (validateFields()) {
            try {
                withContext(Dispatchers.IO) {
                    val messageId = client.sendMessage {
                        it.queueUrl(queue.queueUrl)
                        it.messageBody(inputText.text)
                        if (isFifo) {
                            it.messageDeduplicationId(deduplicationId.text)
                            it.messageGroupId(groupId.text)
                        }
                    }.messageId()

                    statusLabel.text = message("sqs.send.message.success", messageId)
                }
                cache.setMessage(queue.queueUrl, inputText.text)
            } catch (e: Exception) {
                statusLabel.text = message("sqs.failed_to_send_message")
            }
            confirmationPanel.isVisible = true
        }
    }

    private fun validateFields(): Boolean {
        emptyBodyLabel.isVisible = inputText.text.isEmpty()
        if (isFifo) {
            emptyDeduplicationLabel.isVisible = deduplicationId.text.isEmpty()
            emptyGroupLabel.isVisible = groupId.text.isEmpty()
        }

        return !(emptyBodyLabel.isVisible || emptyDeduplicationLabel.isVisible || emptyGroupLabel.isVisible)
    }
}
