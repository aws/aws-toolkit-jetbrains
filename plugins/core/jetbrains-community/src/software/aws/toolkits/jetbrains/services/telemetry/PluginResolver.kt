// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct

class PluginResolver(stackTrace: Array<StackTraceElement> = Thread.currentThread().stackTrace) {
    private val pluginDescriptor by lazy {
        val pluginId = stackTrace
            .withIndex()
            .reversed()
            .filter { it.value.className.contains("software.aws.toolkits") }
            .firstNotNullOfOrNull { PluginUtil.getInstance().getCallerPlugin(it.index) }
        pluginId?.let { PluginManagerCore.getPlugin(it) }
    }

    val product: AWSProduct
        get() = when (pluginDescriptor?.name) {
            "amazon.q" -> AWSProduct.AMAZON_Q_FOR_JET_BRAINS
            else -> AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS
        }

    val version: String
        get() = pluginDescriptor?.version ?: "unknown"

    companion object {
        private val instance = PluginResolver()

        fun getInstance() = instance
    }
}
