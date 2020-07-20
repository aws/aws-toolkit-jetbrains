// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import software.aws.toolkits.resources.message
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.aws.toolkits.jetbrains.services.sqs.Queue
import javax.swing.JLabel

class PollMessagePane(
    private val client: SqsClient,
    private val queue: Queue
): CoroutineScope by ApplicationThreadPoolScope("PollMessagePane"), Disposable {
    lateinit var component: JPanel
    lateinit var messagesTable: JTable
    lateinit var messagesAvailableLabel: JLabel
    private val headers = arrayOf(
        message("sqs.message.message_id"),
        message("sqs.message.message_body"),
        message("sqs.message.sender_id"),
        message("sqs.message.timestamp")
    )

    init {
        requestMessages()
        addTotal()
        // TODO : Wrap cell renderer
    }

    private fun requestMessages() {
        val dataModel = object : DefaultTableModel(0,4) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        dataModel.setColumnIdentifiers(headers)

        val polledMessages : List<Message> = client.receiveMessage {
            it.queueUrl(queue.queueUrl)
            it.attributeNames(QueueAttributeName.ALL)
        }.messages()

        polledMessages.forEach {
            dataModel.addRow(arrayOf(
                it.messageId(),
                it.body(),
                it.attributes().getValue(MessageSystemAttributeName.SENDER_ID),
                it.attributes().getValue(MessageSystemAttributeName.SENT_TIMESTAMP)) // TODO: Fix epoch time
            )
        }

        messagesTable.model = dataModel
    }

    private fun addTotal() {
        val numMessages = client.getQueueAttributes {
            it.queueUrl(queue.queueUrl)
            it.attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        }.attributes().getValue(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)

        messagesAvailableLabel.text += numMessages
    }

    override fun dispose() { }
}
