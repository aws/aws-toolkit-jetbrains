// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AwsCoreBundle
import software.aws.toolkits.resources.message

class QSwitchToMarketplaceVersionAction :
    AnAction(
        message("codewhisperer.actions.switch_to_marketplace.title"),
        null,
        AllIcons.Actions.Refresh
    ),
    DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.project?.let {
            e.presentation.isEnabledAndVisible = PluginUpdateManager.getInstance().isBeta()
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        UpdateSettings.getInstance().storedPluginHosts.remove(CUSTOM_PLUGIN_URL)
        UpdateSettings.getInstance().storedPluginHosts.remove("$CUSTOM_PLUGIN_URL/")

        runInEdt {
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                null,
                message("codewhisperer.actions.switch_to_marketplace.progress.title"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    installMarketplaceAwsPlugins(PluginId.getId(AwsToolkit.CORE_PLUGIN_ID), indicator)
                    installMarketplaceAwsPlugins(PluginId.getId(AwsToolkit.Q_PLUGIN_ID), indicator)
                }
            })
        }
    }

    private fun installMarketplaceAwsPlugins(pluginId: PluginId, indicator: ProgressIndicator) {
        // force update to marketplace version
        try {
            // MarketplaceRequest class is marked as @ApiStatus.Internal
            val descriptor = MarketplaceRequests.loadLastCompatiblePluginDescriptors(setOf(pluginId))
                .find { it.pluginId == pluginId } ?: return

            val downloader = PluginDownloader.createDownloader(descriptor)
            if (!downloader.prepareToInstall(indicator)) return
            downloader.install()

            if (pluginId == PluginId.getId(AwsToolkit.CORE_PLUGIN_ID)) return
            notifyInfo(
                title = AwsCoreBundle.message("aws.notification.auto_update.title", "Amazon Q"),
                content = AwsCoreBundle.message("aws.settings.auto_update.notification.message"),
                project = null,
                notificationActions = listOf(
                    NotificationAction.createSimpleExpiring(AwsCoreBundle.message("aws.settings.auto_update.notification.yes")) {
                        ApplicationManager.getApplication().restart()
                    },
                    NotificationAction.createSimpleExpiring(AwsCoreBundle.message("aws.settings.auto_update.notification.no")) {
                    }
                )
            )
        } catch (e: Exception) {
            return
        }
        return
    }

    companion object {
        private const val CUSTOM_PLUGIN_URL = "https://d244q0w8umigth.cloudfront.net"
    }
}
