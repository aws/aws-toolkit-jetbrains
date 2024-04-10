// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.startup

import software.aws.toolkits.jetbrains.AwsPlugin
import software.aws.toolkits.jetbrains.core.plugin.PluginUpdateManager
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererConfigurable
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.settings.PluginSettings

class QPluginUpdateManager : PluginUpdateManager() {
    override val awsPlugin: AwsPlugin = AwsPlugin.Q
    override val settings: PluginSettings = CodeWhispererSettings.getInstance()
    override val configurableClass = CodeWhispererConfigurable::class.java
}
