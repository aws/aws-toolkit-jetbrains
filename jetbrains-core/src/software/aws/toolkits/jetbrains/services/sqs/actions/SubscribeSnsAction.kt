// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.aws.toolkits.jetbrains.core.explorer.actions.SingleResourceNodeAction
import software.aws.toolkits.jetbrains.services.sqs.SqsQueueNode
import software.aws.toolkits.jetbrains.services.sqs.SubscribeSnsDialog
import software.aws.toolkits.resources.message

class SubscribeSnsAction : SingleResourceNodeAction<SqsQueueNode>(message("sqs.subscribe.sns")), DumbAware {
    override fun actionPerformed(selected: SqsQueueNode, e: AnActionEvent) {
        val project = selected.nodeProject
        val queue = selected.queue
        SubscribeSnsDialog(project, queue).show()
    }
}
