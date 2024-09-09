// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.AwsSettingsSharedConfigurable
import software.aws.toolkits.resources.AwsCoreBundle

class AwsTelemetryPrompter : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (AwsSettings.getInstance().promptedForTelemetry || System.getProperty("aws.telemetry.skip_prompt", null)?.toBoolean() == true) {
            return
        }

        val notification = Notification(
            "aws.toolkit_telemetry",
            AwsCoreBundle.message("aws.settings.telemetry.prompt.title"),
            AwsCoreBundle.message("aws.settings.telemetry.prompt.message"),
            NotificationType.INFORMATION
        ).also {
            it.setListener { notification, _ ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AwsSettingsSharedConfigurable::class.java)
                notification.expire()
            }
        }

        notification.notify(project)

        AwsSettings.getInstance().promptedForTelemetry = true
    }
}
