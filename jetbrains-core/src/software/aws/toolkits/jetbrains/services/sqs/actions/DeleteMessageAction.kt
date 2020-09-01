// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

class DeleteMessageAction(
    private val project: Project,
    private val client: SqsClient,
    private val table: TableView<Message>,
    private val queueUrl: String
) : CoroutineScope by ApplicationThreadPoolScope("DeleteMessageAction"),
    DumbAwareAction(message("sqs.delete.message.action"), null, AllIcons.Actions.Cancel) {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = table.selectedRows.size == 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val row = table.selectedRow
        val message = table.selectedObject ?: return
        launch {
            try {
                client.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }
                table.remove(row)
                notifyInfo(
                    project = project,
                    title = message("sqs.delete.message.succeeded", message.messageId())
                )
            } catch (e: Exception) {
                notifyError(
                    project = project,
                    content = message("sqs.delete.message.failed", message.messageId())
                )
            }
        }
    }
}
