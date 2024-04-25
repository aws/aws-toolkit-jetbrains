// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.startup

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import org.slf4j.LoggerFactory
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.jetbrains.AwsToolkit

private val LOG = LoggerFactory.getLogger("PluginInstallUtil")

internal fun lookForPluginToInstall(pluginId: PluginId, progressIndicator: ProgressIndicator): Boolean {
    try {
        // MarketplaceRequest class is marked as @ApiStatus.Internal
        val descriptor = MarketplaceRequests.loadLastCompatiblePluginDescriptors(setOf(pluginId))
            .find { it.pluginId == pluginId } ?: return false

        val downloader = PluginDownloader.createDownloader(descriptor)
        if (!downloader.prepareToInstall(progressIndicator)) return false
        downloader.install()
    } catch (e: Exception) {
        LOG.error(e) { "Unable to auto-install $pluginId" }
        return false
    }
    return true
}
