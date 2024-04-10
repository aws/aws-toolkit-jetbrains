// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.plugins

import software.aws.toolkits.jetbrains.AwsPlugin
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.AwsSettingsConfigurable
import software.aws.toolkits.jetbrains.settings.PluginSettings

class ToolkitPluginUpdateManager : PluginUpdateManager() {
    override val awsPlugin: AwsPlugin = AwsPlugin.TOOLKIT
    override val settings: PluginSettings = AwsSettings.getInstance()
    override val configurableClass = AwsSettingsConfigurable::class.java
}
