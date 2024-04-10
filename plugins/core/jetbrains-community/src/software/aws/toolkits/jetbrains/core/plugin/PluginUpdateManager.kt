// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.plugin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.PluginDownloader.compareVersionsSkipBrokenAndIncompatible
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.AwsPlugin
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.settings.PluginSettings
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.ToolkitTelemetry

abstract class PluginUpdateManager {
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    abstract val awsPlugin: AwsPlugin
    abstract val settings: PluginSettings
    abstract val configurableClass: Class<out Configurable>
    private val pluginId by lazy {
        if (awsPlugin == AwsPlugin.TOOLKIT) AwsToolkit.PLUGIN_ID else AwsToolkit.Q_PLUGIN_ID
    }
    private val pluginDescriptor by lazy {
        if (awsPlugin == AwsPlugin.TOOLKIT) AwsToolkit.DESCRIPTOR else AwsToolkit.Q_DESCRIPTOR
    }
    private val pluginName by lazy {
        if (awsPlugin == AwsPlugin.TOOLKIT) "AWS Toolkit" else "Amazon Q"
    }

    fun scheduleAutoUpdate() {
        if (alarm.isDisposed) return
        scheduleUpdateTask()

        val enabled = settings.isAutoUpdateEnabled
        LOG.debug { "$pluginName checking for new updates. Auto update enabled: $enabled" }

        if (!enabled) return

        runInEdt {
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(null, message("aws.settings.auto_update.progress.message", pluginName)) {
                    override fun run(indicator: ProgressIndicator) {
                        checkForUpdates(indicator)
                    }
                }
            )
        }
    }

    private fun scheduleUpdateTask() {
        alarm.addRequest({ scheduleAutoUpdate() }, UPDATE_CHECK_INTERVAL_IN_MS)
    }

    @RequiresBackgroundThread
    fun checkForUpdates(progressIndicator: ProgressIndicator) {
        // Note: This will need to handle exceptions and ensure thread-safety
        try {
            // wasUpdatedWithRestart means that, it was an update and it needs to restart to apply
            if (InstalledPluginsState.getInstance().wasUpdatedWithRestart(PluginId.getId(pluginId))) {
                LOG.debug { "$pluginName was recently updated and needed restart, not performing auto-update again" }
                return
            }

            val pluginDescriptor = pluginDescriptor as IdeaPluginDescriptor? ?: return
            if (pluginDescriptor.version.contains("SNAPSHOT", ignoreCase = true)) {
                LOG.debug { "$pluginName is a SNAPSHOT version, not performing auto-update" }
                return
            }
            if (!pluginDescriptor.isEnabled) {
                LOG.debug { "$pluginName is disabled, not performing auto-update" }
                return
            }
            LOG.debug { "Current version: ${pluginDescriptor.version}" }
            val latestPluginDownloader = getUpdate(pluginDescriptor)
            if (latestPluginDownloader == null) {
                LOG.debug { "$pluginName no newer version found, not performing auto-update" }
                return
            } else {
                LOG.debug { "$pluginName found newer version: ${latestPluginDownloader.pluginVersion}" }
            }

            if (!latestPluginDownloader.prepareToInstall(progressIndicator)) return
            latestPluginDownloader.install()
            // TODO: distinguish telemetry
            ToolkitTelemetry.showAction(
                project = null,
                success = true,
                id = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                source = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                component = Component.Filesystem
            )
        } catch (e: Exception) {
            LOG.debug(e) { "Unable to update $pluginName" }
            // TODO: distinguish telemetry
            ToolkitTelemetry.showAction(
                project = null,
                success = false,
                id = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                source = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                component = Component.Filesystem,
                reason = e.message
            )
            return
        } catch (e: Error) {
            // Handle cases like NoSuchMethodError when the API is not available in certain versions
            LOG.debug(e) { "Unable to update $pluginName" }
            // TODO: distinguish telemetry
            ToolkitTelemetry.showAction(
                project = null,
                success = false,
                id = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                source = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                component = Component.Filesystem,
                reason = e.message
            )
            return
        }
        if (!settings.isAutoUpdateNotificationEnabled) return
        notifyInfo(
            title = message("aws.notification.auto_update.title", pluginName),
            content = message("aws.settings.auto_update.notification.message"),
            project = null,
            notificationActions = listOf(
                NotificationAction.createSimpleExpiring(message("aws.settings.auto_update.notification.yes")) {
                    // TODO: distinguish telemetry
                    ToolkitTelemetry.invokeAction(
                        project = null,
                        result = Result.Succeeded,
                        id = "autoUpdateActionRestart",
                        source = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                        component = Component.Filesystem
                    )
                    ApplicationManager.getApplication().restart()
                },
                NotificationAction.createSimpleExpiring(message("aws.settings.auto_update.notification.no")) {
                    // TODO: distinguish telemetry
                    ToolkitTelemetry.invokeAction(
                        project = null,
                        result = Result.Succeeded,
                        id = "autoUpdateActionNotNow",
                        source = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                        component = Component.Filesystem
                    )
                },
                NotificationAction.createSimple(message("aws.notification.auto_update.settings.title")) {
                    // TODO: distinguish telemetry
                    ToolkitTelemetry.invokeAction(
                        project = null,
                        result = Result.Succeeded,
                        id = ID_ACTION_AUTO_UPDATE_SETTINGS,
                        source = SOURCE_AUTO_UPDATE_FINISH_NOTIFY,
                        component = Component.Filesystem
                    )
                    ShowSettingsUtil.getInstance().showSettingsDialog(null, configurableClass)
                }
            )
        )
    }

    fun getUpdate(pluginDescriptor: IdeaPluginDescriptor): PluginDownloader? =
        getUpdateInfo().firstOrNull {
            it.id == pluginDescriptor.pluginId &&
                compareVersionsSkipBrokenAndIncompatible(it.pluginVersion, pluginDescriptor) > 0
        }

    // TODO: Optimize this to only search the result for Aws Toolkit or Amazon Q
    fun getUpdateInfo(): Collection<PluginDownloader> = UpdateChecker.getPluginUpdates() ?: emptyList()

    fun notifyAutoUpdateFeature(project: Project) {
        notifyInfo(
            title = message("aws.notification.auto_update.feature_intro.title", pluginName),
            project = project,
            notificationActions = listOf(
                NotificationAction.createSimpleExpiring(message("aws.notification.auto_update.feature_intro.ok")) {},
                NotificationAction.createSimple(message("aws.notification.auto_update.settings.title")) {
                    // TODO: distinguish telemetry
                    ToolkitTelemetry.invokeAction(
                        project = null,
                        result = Result.Succeeded,
                        id = ID_ACTION_AUTO_UPDATE_SETTINGS,
                        source = SOURCE_AUTO_UPDATE_FEATURE_INTRO_NOTIFY,
                        component = Component.Filesystem
                    )
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, configurableClass)
                }
            )
        )
    }

    companion object {
        fun getInstance(): PluginUpdateManager = service()
        private val LOG = getLogger<PluginUpdateManager>()
        private const val UPDATE_CHECK_INTERVAL_IN_MS = 4 * 60 * 60 * 1000 // 4 hours
        private const val SOURCE_AUTO_UPDATE_FINISH_NOTIFY = "autoUpdateFinishNotification"
        const val SOURCE_AUTO_UPDATE_FEATURE_INTRO_NOTIFY = "autoUpdateFeatureIntroNotification"
        const val ID_ACTION_AUTO_UPDATE_SETTINGS = "autoUpdateActionSettings"
    }
}
