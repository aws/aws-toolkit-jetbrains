// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import software.amazon.awssdk.services.toolkittelemetry.model.AWSProduct

/**
 * Responsible for resolving the plugin descriptor and determining the AWS product
 * and version based on the stack trace of the calling thread or a provided stack trace.
 */
class PluginResolver private constructor(callerStackTrace: Array<StackTraceElement>) {
    private val pluginDescriptor by lazy {
        callerStackTrace
            .reversed()
            .filter { it.className.startsWith("software.aws.toolkits") }
            .firstNotNullOfOrNull { PluginManagerCore.getPluginDescriptorOrPlatformByClassName(it.className) }
    }

    val product: AWSProduct =
        when (pluginDescriptor?.pluginId?.idString) {
            "amazon.q" -> AWSProduct.AMAZON_Q_FOR_JET_BRAINS
            else -> AWSProduct.AWS_TOOLKIT_FOR_JET_BRAINS
        }

    val version = pluginDescriptor?.version ?: "unknown"

    companion object {
        private val threadLocalResolver = ThreadLocal<PluginResolver>()

        /**
         * Creates a new PluginResolver instance off the current thread's stack trace, or retrieves
         * the existing thread-local resolver if it is set. If a value did not previously exist,
         * the new instance is stored in the thread-local.
         */
        fun fromCurrentThread() = threadLocalResolver.get() ?: PluginResolver(Thread.currentThread().stackTrace).also {
            threadLocalResolver.set(it)
        }

        /**
         * Creates a new PluginResolver instance from a provided stack trace.
         */
        fun fromStackTrace(stackTrace: Array<StackTraceElement>) = PluginResolver(stackTrace)

        /**
         * Sets the PluginResolver instance in a thread-local for the current thread.
         * This is useful
         */
        fun setThreadLocal(value: PluginResolver) {
            threadLocalResolver.set(value)
        }
    }
}
