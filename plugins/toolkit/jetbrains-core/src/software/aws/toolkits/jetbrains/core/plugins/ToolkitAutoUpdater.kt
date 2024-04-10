// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.plugins

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.util.concurrent.atomic.AtomicBoolean

// Most logic are similar and referenced from CodeWhispererProjectStartupActivity
class ToolkitAutoUpdater : StartupActivity.DumbAware {
    private val autoUpdateRunOnce = AtomicBoolean(false)

    override fun runActivity(project: Project) {
        // We want the auto-update feature to be triggered only once per running application
        if (!autoUpdateRunOnce.getAndSet(true)) {
            PluginUpdateManager.getInstance().scheduleAutoUpdate()
            if (!AwsSettings.getInstance().isAutoUpdateFeatureNotificationShownOnce) {
                PluginUpdateManager.getInstance().notifyAutoUpdateFeature(project)
                AwsSettings.getInstance().isAutoUpdateFeatureNotificationShownOnce = true
            }
        }
    }
}
