// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.amazon.awssdk.services.sqs.SqsClient
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.sqs.EditAttributesDialog
import software.aws.toolkits.jetbrains.services.sqs.SqsQueueNode
import software.aws.toolkits.resources.message

class EditAttributesAction : SingleResourceNodeAction<SqsQueueNode>(message("sqs.edit.attributes")), DumbAware {
    override fun actionPerformed(selected: SqsQueueNode, e: AnActionEvent) {
        EditAttributesDialog(selected.nodeProject, selected.nodeProject.awsClient(), selected.queue).show()
    }
}
