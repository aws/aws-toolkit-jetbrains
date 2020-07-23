// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.resources.message
import javax.swing.JLabel
import javax.swing.JPanel

class PollMessagePane(
    private val project: Project,
    private val queue: Queue
) : Disposable {
    lateinit var component: JPanel
    lateinit var messagesAvailableLabel: JLabel
    lateinit var tablePanel: SimpleToolWindowPanel

    private val client: SqsClient = project.awsClient()
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

    private fun requestMessages() {
        val polledMessages: List<Message> = client.receiveMessage {
            it.queueUrl(queue.queueUrl)
            it.attributeNames(QueueAttributeName.ALL)
            it.maxNumberOfMessages(MAX_NUMBER_OF_MESSAGES)
        }.messages()

        polledMessages.forEach { messagesTable.tableModel.addRow(it) }
        messagesTable.showBusy(busy = false)
    }

    private fun addTotal() {
        val numMessages = client.getQueueAttributes {
            it.queueUrl(queue.queueUrl)
            it.attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        }.attributes().getValue(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)

        messagesAvailableLabel.text += numMessages
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

    private fun refreshTable() {
        for (x in 0 until messagesTable.tableModel.rowCount) {
            messagesTable.tableModel.removeRow(x)
        }
        requestMessages()
    }

    override fun dispose() {}

    private companion object {
        const val MAX_NUMBER_OF_MESSAGES = 10
    }
}
