// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.notification

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import software.aws.toolkits.resources.message

class AwsCorePluginNotice : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        if (PropertiesComponent.getInstance().getBoolean(IGNORE_PROMPT)) return
        if (!PluginManagerCore.isPluginInstalled(PluginId.getId(CORE_PLUGIN_ID))) return

        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("aws.toolkit_core_notice")
        notificationGroup.createNotification(
            message("aws.toolkit_core_notice.title"),
            message("aws.toolkit_core_notice.message"),
            NotificationType.INFORMATION
        )
            .addAction(
                NotificationAction.createSimpleExpiring(message("aws.toolkit_core_notice.manage_plugins")) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        PluginManagerConfigurable::class.java
                    ) { configurable: PluginManagerConfigurable ->
                        configurable.openInstalledTab(CORE_PLUGIN_NAME)
                    }
                }
            )
            .addAction(
                NotificationAction.createSimpleExpiring(message("general.notification.action.hide_forever")) {
                    PropertiesComponent.getInstance().setValue(IGNORE_PROMPT, true)
                }
            )
            .notify(project)
    }

    companion object {
        const val CORE_PLUGIN_ID = "aws.toolkit.core"
        const val CORE_PLUGIN_NAME = "AWS Core"
        const val IGNORE_PROMPT = "aws.toolkit_core_notice.dismissed"
    }
}
