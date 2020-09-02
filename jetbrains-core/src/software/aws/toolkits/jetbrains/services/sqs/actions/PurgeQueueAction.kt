// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sqs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import software.aws.toolkits.resources.message

class PurgeQueueAction : DumbAwareAction(
    message("sqs.purge_queue"),
    null,
    AllIcons.Actions.Cancel
) {
    override fun actionPerformed(e: AnActionEvent) {
        TODO("Not yet implemented")
    }
}
