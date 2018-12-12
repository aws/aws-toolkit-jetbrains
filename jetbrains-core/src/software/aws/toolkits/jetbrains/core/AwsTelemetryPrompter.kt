// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.AwsSettingsConfigurable
import software.aws.toolkits.resources.message

internal const val GROUP_DISPLAY_ID = "AWS Telemetry"

class AwsTelemetryPrompter : StartupActivity {

    override fun runActivity(project: Project) {
        if (!AwsSettings.getInstance().promptedForTelemetry) {
            val group = NotificationGroup(GROUP_DISPLAY_ID, NotificationDisplayType.STICKY_BALLOON, true)

            val notification = group.createNotification(
                message("aws.settings.telemetry.prompt.title"), message("aws.settings.telemetry.prompt.message"), NotificationType.INFORMATION
            ) { notification, _ ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AwsSettingsConfigurable::class.java)
                notification.expire()
            }

            Notifications.Bus.notify(notification, project)

            AwsSettings.getInstance().promptedForTelemetry = true
        }
    }
}