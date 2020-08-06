// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.actions
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import icons.AwsIcons
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.services.sqs.CreateQueueDialog
import software.aws.toolkits.resources.message

class CreateQueueAction : DumbAwareAction(
    message("sqs.create.queue.title"),
    null,
    AwsIcons.Resources.Sqs.SQS_QUEUE
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(LangDataKeys.PROJECT)
        val client: SqsClient = project.awsClient()
        val dialog = CreateQueueDialog(project, client)
        dialog.show()
    }
}
