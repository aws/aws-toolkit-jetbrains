// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.plugin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.org.PluginManagerFilters

class QBetaPluginManagementPolicy: PluginManagementPolicy {
    override fun canEnablePlugin(descriptor: IdeaPluginDescriptor?): Boolean {
        return descriptor?.let { PluginManagerFilters.getInstance().allowInstallingPlugin(it) } ?: true
    }

    override fun canInstallPlugin(descriptor: IdeaPluginDescriptor?): Boolean {
        return canEnablePlugin(descriptor)
    }

    override fun isDowngradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean {
        return true
    }

    override fun isInstallFromDiskAllowed(): Boolean {
        return PluginManagerFilters.getInstance().allowInstallFromDisk()
    }

    override fun isUpgradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean {
        return true
    }
}
