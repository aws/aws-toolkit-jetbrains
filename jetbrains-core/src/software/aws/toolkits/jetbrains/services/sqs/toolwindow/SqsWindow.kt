// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import icons.AwsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowManager
import software.aws.toolkits.jetbrains.core.toolwindow.ToolkitToolWindowType
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.getCoroutineUiContext
import software.aws.toolkits.resources.message

class SqsWindow(private val project: Project) : CoroutineScope by ApplicationThreadPoolScope("SqsWindow") {
    private val toolWindow = ToolkitToolWindowManager.getInstance(project, SQS_TOOL_WINDOW)
    private val edtContext = getCoroutineUiContext()
    private val client: SqsClient = project.awsClient()

    fun pollMessage(queue: Queue) {
        showQueue(queue, SqsWindowUi(client, queue).apply { pollMessage() })
    }

    fun sendMessage(queue: Queue) {
        showQueue(queue, SqsWindowUi(client, queue).apply { sendMessage() })
    }

    private fun showQueue(queue: Queue, component: SqsWindowUi) = launch {
        try {
            withContext(edtContext) {
                toolWindow.find(queue.queueUrl)?.show() ?: toolWindow.addTab(queue.queueName, component.mainPanel, activate = true, id = queue.queueUrl)
            }
        } catch (e: Exception) {
            LOG.error(e) { "Exception thrown while trying to open queue '${queue.queueName}'" }
            throw e
        }
    }

    fun closeQueue(queueUrl: String) = runBlocking {
        withContext(edtContext) {
            toolWindow.find(queueUrl)?.dispose()
        }
    }

    companion object {
        internal val SQS_TOOL_WINDOW = ToolkitToolWindowType(
            "AWS.Sqs",
            message("sqs.toolwindow"),
            AwsIcons.Resources.Sqs.SQS_TOOL_WINDOW
        )

        fun getInstance(project: Project): SqsWindow = project.service()

        private val LOG = getLogger<SqsWindow>()
    }
}
