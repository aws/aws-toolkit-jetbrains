// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path
import java.nio.file.Paths

object AwsToolkit {
    const val TOOLKIT_PLUGIN_ID = "aws.toolkit"

    // TODO: change this to real plugin id
    const val Q_PLUGIN_ID = "plugin-amazonq"

    // TODO: change this
    const val CORE_PLUGIN_ID = "plugin-core"

    private val TOOLKIT_PLUGIN_INFO = PluginInfo(TOOLKIT_PLUGIN_ID)
    private val Q_PLUGIN_INFO = PluginInfo(Q_PLUGIN_ID)
    private val CORE_PLUGIN_INFO = PluginInfo(CORE_PLUGIN_ID)

    val PLUGINS_INFO = mapOf(
        AwsPlugin.TOOLKIT to TOOLKIT_PLUGIN_INFO,
        AwsPlugin.Q to Q_PLUGIN_INFO,
        AwsPlugin.CORE to CORE_PLUGIN_INFO
    )

    const val GITHUB_URL = "https://github.com/aws/aws-toolkit-jetbrains"
    const val AWS_DOCS_URL = "https://docs.aws.amazon.com/console/toolkit-for-jetbrains"
    const val TOOLKIT_PLUGIN_NAME = "AWS Toolkit"
}

data class PluginInfo(val id: String) {
    val descriptor: PluginDescriptor? = PluginManagerCore.getPlugin(PluginId.getId(id))
    val name: String = when (id) {
        AwsToolkit.TOOLKIT_PLUGIN_ID -> "AWS Toolkit"
        AwsToolkit.Q_PLUGIN_ID -> "Amazon Q"
        // TODO: change name
        AwsToolkit.CORE_PLUGIN_ID -> "AWS Plugin Core"
        else -> "AWS Toolkit"
    }
    val version: String = descriptor?.version ?: "Unknown"
    val path: Path? =
        if (ApplicationManager.getApplication().isUnitTestMode) {
            Paths.get(System.getProperty("plugin.path"))
        } else {
            descriptor?.pluginPath
        }
}

enum class AwsPlugin {
    TOOLKIT,
    Q,
    CORE
}
