// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.CustomPluginRepositoryService
import com.intellij.ide.plugins.PluginManagementPolicy
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
import software.aws.toolkits.jetbrains.AwsPlugin
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.AwsCoreBundle
import software.aws.toolkits.resources.message

class QSwitchToMarketplaceVersionAction:
    AnAction(
        "Switch Back to Marketplace",
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
        // remove all custom channel for amazon q and aws core
        val qId = PluginId.getId(AwsToolkit.Q_PLUGIN_ID)
        val coreId = PluginId.getId(AwsToolkit.CORE_PLUGIN_ID)
        val customPlugins = CustomPluginRepositoryService.getInstance().customRepositoryPluginMap
        val result = customPlugins.filter {
            it.value.any { node ->
                listOf(qId, coreId).contains(node.pluginId)
            }
        }
        result.keys.forEach {
            customPlugins.remove(it)
        }

        runInEdt {
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                null,
                "Switching to marketplace version",
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    installMarketplaceAwsPlugins(PluginId.getId(AwsToolkit.CORE_PLUGIN_ID), indicator)
                    installMarketplaceAwsPlugins(qId, indicator)
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
}
