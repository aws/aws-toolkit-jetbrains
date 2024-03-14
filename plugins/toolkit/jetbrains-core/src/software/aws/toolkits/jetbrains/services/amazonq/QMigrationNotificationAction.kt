// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.AwsIcons
import software.aws.toolkits.resources.message

// PR TODO: change icon
class QMigrationNotificationAction : AnAction(message("q.migration.notification.title"), null, AwsIcons.Logos.AWS_Q) {
    override fun actionPerformed(e: AnActionEvent) {}
}
