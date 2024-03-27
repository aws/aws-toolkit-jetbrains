// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.startup

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.AwsToolkit.Q_PLUGIN_ID
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererExpired
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isQEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.importadder.CodeWhispererImportAdderListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager.Companion.CODEWHISPERER_USER_ACTION_PERFORMED
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.FEATURE_CONFIG_POLL_INTERVAL_IN_MS
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.calculateIfIamIdentityCenterConnection
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.AwsSettingsConfigurable
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.ToolkitTelemetry
import java.util.concurrent.atomic.AtomicBoolean

// TODO: add logics to check if we want to remove recommendation suspension date when user open the IDE
class CodeWhispererProjectStartupActivity : StartupActivity.DumbAware {
    private var runOnce = false
    private val autoUpdateRunOnce = AtomicBoolean(false)
    private val qMigrationShownOnce = AtomicBoolean(false)

    /**
     * Should be invoked when
     * (1) new users accept CodeWhisperer ToS (have to be triggered manually))
     * (2) existing users open the IDE (automatically triggered)
     */
    override fun runActivity(project: Project) {
        // We want the auto-update feature to be triggered only once per running application
        if (!autoUpdateRunOnce.getAndSet(true)) {
            QPluginUpdateManager.getInstance()
            if (!AwsSettings.getInstance().isAutoUpdateFeatureNotificationShownOnce) {
                notifyAutoUpdateFeature(project)
                AwsSettings.getInstance().isAutoUpdateFeatureNotificationShownOnce = true
            }
        }

        // TODO: Should be eventually moved to Toolkit only startup activity
        if (!qMigrationShownOnce.getAndSet(true)) {
            displayQMigrationInfo(project)
        }

        if (!isCodeWhispererEnabled(project)) return

        // ---- Everything below will be triggered only when CW is enabled, everything above will be triggered once per project ----

        if (runOnce) return

        // Reconnect CodeWhisperer on startup
        promptReAuth(project, isPluginStarting = true)
        if (isCodeWhispererExpired(project)) return

        // Init featureConfig job
        initFeatureConfigPollingJob(project)

        calculateIfIamIdentityCenterConnection(project) {
            ApplicationManager.getApplication().executeOnPooledThread {
                CodeWhispererModelConfigurator.getInstance().listCustomizations(project, passive = true)
            }
        }

        // install intellsense autotrigger listener, this only need to be executed once
        project.messageBus.connect().subscribe(LookupManagerListener.TOPIC, CodeWhispererIntelliSenseAutoTriggerListener)
        project.messageBus.connect().subscribe(CODEWHISPERER_USER_ACTION_PERFORMED, CodeWhispererImportAdderListener)

        runOnce = true
    }

    // For the Q migration notification, we want to notify it only once the first time user has updated Toolkit,
    // if we have detected Q is not yet installed.
    // Check the user's current connection, if it contains CW or Q, auto-install for them, if they don't have one,
    // it means they have not used CW or Q before so show the opt-in/install notification for them.
    private fun displayQMigrationInfo(project: Project) {
        if (AwsSettings.getInstance().isQMigrationNotificationShownOnce) return

        val hasUsedCodeWhisperer = isCodeWhispererEnabled(project)
        val hasUsedQ = isQEnabled(project)
        if (hasUsedCodeWhisperer || hasUsedQ) {
            // do auto-install
            installQPlugin(project, autoInstall = true)
        } else {
            // show opt-in/install notification
            notifyInfo(
                // TODO: change text
                title = "Hey we have a new extension, you should install",
                project = project,
                notificationActions = listOf(
                    NotificationAction.createSimpleExpiring("Install") {
                        installQPlugin(project, autoInstall = false)
                    }
                    // TODO: other actions?
                )
            )
        }
        AwsSettings.getInstance().isQMigrationNotificationShownOnce = true
    }

    private fun installQPlugin(project: Project, autoInstall: Boolean) {
        val qPluginId = PluginId.getId(Q_PLUGIN_ID)
        if (PluginManagerCore.isPluginInstalled(qPluginId)) {
            LOG.debug { "Amazon Q plugin is already installed, not performing migration" }
            return
        }

        runInEdt {
            ProgressManager.getInstance().run(
                // TODO: change title
                object : Task.Backgroundable(project, "Installing Amazon Q...") {
                    override fun run(indicator: ProgressIndicator) {
                        val succeeded = lookForPluginToInstall(indicator)
                        if (succeeded) {
                            if (!autoInstall) {
                                PluginManagerMain.notifyPluginsUpdated(project)
                            } else {
                                notifyInfo(
                                    // TODO: change text
                                    title = "Hey we've auto-installed because you use Q",
                                    project = project,
                                    notificationActions = listOf(
                                        NotificationAction.createSimpleExpiring("Restart") {
                                            ApplicationManager.getApplication().restart()
                                        }
                                    )
                                )
                            }
                        } else {
                            notifyError(
                                // TODO: change text
                                title = "Failed to auto-install, please go install manually",
                                project = project,
                                notificationActions = listOf(
                                    NotificationAction.createSimpleExpiring("Go to plugin management") {
                                        // TODO: change search text
                                        ShowSettingsUtil.getInstance().showSettingsDialog(
                                            project,
                                            PluginManagerConfigurable::class.java
                                        ) { configurable: PluginManagerConfigurable ->
                                            configurable.openMarketplaceTab("Amazon Q")
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    private fun lookForPluginToInstall(progressIndicator: ProgressIndicator): Boolean {
        try {
            val qPluginId = PluginId.getId(Q_PLUGIN_ID)

            // MarketplaceRequest class is marked as @ApiStatus.Internal
            val descriptor = MarketplaceRequests.loadLastCompatiblePluginDescriptors(setOf(qPluginId))
                .find { it.pluginId == qPluginId } ?: return false

            val downloader = PluginDownloader.createDownloader(descriptor)
            if (!downloader.prepareToInstall(progressIndicator)) return false
            downloader.install()
        } catch (e: Exception) {
            LOG.error(e) { "Unable to auto-install Amazon Q" }
            return false
        }
        return true
    }

    private fun notifyAutoUpdateFeature(project: Project) {
        notifyInfo(
            title = message("aws.notification.auto_update.feature_intro.title"),
            project = project,
            notificationActions = listOf(
                NotificationAction.createSimpleExpiring(message("aws.notification.auto_update.feature_intro.ok")) {},
                NotificationAction.createSimple(message("aws.notification.auto_update.settings.title")) {
                    ToolkitTelemetry.invokeAction(
                        project = null,
                        result = Result.Succeeded,
                        id = QPluginUpdateManager.ID_ACTION_AUTO_UPDATE_SETTINGS,
                        source = QPluginUpdateManager.SOURCE_AUTO_UPDATE_FEATURE_INTRO_NOTIFY,
                        component = Component.Filesystem
                    )
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AwsSettingsConfigurable::class.java)
                }
            )
        )
    }

    // Start a job that runs every 30 mins
    private fun initFeatureConfigPollingJob(project: Project) {
        projectCoroutineScope(project).launch {
            while (isActive) {
                CodeWhispererFeatureConfigService.getInstance().fetchFeatureConfigs(project)
                delay(FEATURE_CONFIG_POLL_INTERVAL_IN_MS)
            }
        }
    }

    companion object {
        private val LOG = getLogger<CodeWhispererProjectStartupActivity>()
    }
}
