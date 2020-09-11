// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueInProgressException
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.sqs.Queue
import software.aws.toolkits.jetbrains.services.sqs.telemetryType
import software.aws.toolkits.jetbrains.utils.ApplicationThreadPoolScope
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.SqsTelemetry

class PurgeQueueAction(
    private val project: Project,
    private val client: SqsClient,
    private val queue: Queue
) : DumbAwareAction(message("sqs.purge_queue.action")), CoroutineScope by ApplicationThreadPoolScope("PurgeQueueAction") {
    override fun actionPerformed(e: AnActionEvent) {
        val response = Messages.showOkCancelDialog(
            project,
            message("sqs.purge_queue.confirm", queue.queueName),
            message("sqs.purge_queue.confirm.title"),
            CommonBundle.getOkButtonText(),
            CommonBundle.getCancelButtonText(),
            Messages.getWarningIcon()
        )
        if (response != Messages.OK) {
            SqsTelemetry.purgeQueue(project, Result.Cancelled, queue.telemetryType())
            return
        }
        launch {
            try {
                client.purgeQueue { it.queueUrl(queue.queueUrl) }
                LOG.info { "Started purging ${queue.queueUrl}" }
                notifyInfo(
                    project = project,
                    title = message("aws.notification.title"),
                    content = message("sqs.purge_queue.succeeded", queue.queueUrl)
                )
                SqsTelemetry.purgeQueue(project, Result.Succeeded, queue.telemetryType())
            } catch (e: PurgeQueueInProgressException) {
                LOG.warn { "${queue.queueUrl} already has a query purge in progress" }
                notifyError(
                    project = project,
                    content = message("sqs.purge_queue.failed.60_seconds", queue.queueUrl)
                )
                SqsTelemetry.purgeQueue(project, Result.Succeeded, queue.telemetryType())
            } catch (e: Exception) {
                LOG.error(e) { "Exception thrown while trying to purge query ${queue.queueUrl}" }
                notifyError(
                    project = project,
                    content = message("sqs.purge_queue.failed", queue.queueUrl)
                )
                SqsTelemetry.purgeQueue(project, Result.Failed, queue.telemetryType())
            }
        }
    }

    private companion object {
        val LOG = getLogger<PurgeQueueAction>()
    }
}
