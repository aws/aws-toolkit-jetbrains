// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName

val LOG = Logger.getInstance("software.aws.toolkits.jetbrains.services.amazonq.lsp.util.ExtensionPointUtils")

inline fun <T : Any> ExtensionPointName<T>.forEachExtensionSafe(action: (T) -> Unit) {
    for (extension in extensionList) {
        try {
            action(extension)
        } catch (e: PluginException) {
            LOG.warn("Failed to process extension ${extension::class.java.name}", e)
        } catch (e: Exception) {
            LOG.warn("Error processing extension ${extension::class.java.name}", e)
        }
    }
}
