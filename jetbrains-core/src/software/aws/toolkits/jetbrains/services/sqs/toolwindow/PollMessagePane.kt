// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.SimpleToolWindowPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.aws.toolkits.jetbrains.services.sqs.MAX_NUMBER_OF_MESSAGES
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel

class PollMessagePane(
    private val client: SqsClient,
    private val queue: Queue
) : CoroutineScope by ApplicationThreadPoolScope("PollMessagesPane") {
    lateinit var component: JPanel
    lateinit var messagesAvailableLabel: JLabel
    lateinit var tablePanel: SimpleToolWindowPanel
    val messagesTable = MessagesTable()

    private fun createUIComponents() {
        tablePanel = SimpleToolWindowPanel(false, true)
    }

    init {
        tablePanel.setContent(messagesTable.component)

        requestMessages()
        addTotal()
        addToolbar()
    }

    private fun requestMessages() = launch {
        try {
            val polledMessages: List<Message> = client.receiveMessage {
                it.queueUrl(queue.queueUrl)
                it.attributeNames(QueueAttributeName.ALL)
                it.maxNumberOfMessages(MAX_NUMBER_OF_MESSAGES)
            }.messages()

            polledMessages.forEach { messagesTable.tableModel.addRow(it) }
            messagesTable.setBusy(busy = false)
        } catch (e: Exception) {
            messagesTable.table.emptyText.text = message("sqs.failed_to_poll_messages")
        }
    }

    private fun addTotal() {
        try {
            val numMessages = client.getQueueAttributes {
                it.queueUrl(queue.queueUrl)
                it.attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
            }.attributes().getValue(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)

            messagesAvailableLabel.text = message("sqs.messages.available.text") + numMessages
        } catch (e: Exception) {
            messagesAvailableLabel.text = message("sqs.failed_to_load_total")
        }
    }

    private fun addToolbar() {
        val actionGroup = DefaultActionGroup()
        actionGroup.addAction(object : AnAction(message("general.refresh"), null, AllIcons.Actions.Refresh), DumbAware {
            override fun actionPerformed(e: AnActionEvent) {
                refreshTable()
            }
        })
        tablePanel.toolbar = ActionManager.getInstance().createActionToolbar("PollMessagePane", actionGroup, false).component
    }

    // TODO: Add message table actions

    private fun refreshTable() = launch {
        messagesTable.setBusy(busy = true)
        // Remove all entries
        while (messagesTable.tableModel.rowCount != 0) {
            messagesTable.tableModel.removeRow(0)
        }
        requestMessages()
        addTotal()
    }
}
