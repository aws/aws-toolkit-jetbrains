// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.intellij

import java.io.File

/**
 * Gateway 2026.1 product-info.json has moduleV2/productModuleV2 layout entries without
 * "classPath". intellij-plugin-structure declares ProductModuleV2.classPath as non-null
 * Kotlin parameter, so Jackson throws NPE during deserialization.
 *
 * Bug exists on master: https://github.com/JetBrains/intellij-plugin-verifier
 * (intellij-plugin-structure/structure-intellij/.../ProductInfo.kt)
 *
 * Patches cached product-info.json to add empty classPath arrays where missing.
 */
object ProductInfoPatcher {
    private val MISSING_CLASSPATH = Regex(
        """(?s)"kind"\s*:\s*"(moduleV2|productModuleV2)"\s*\}"""
    )

    fun patchGatewayProductInfo(gradleUserHome: File) {
        val caches = gradleUserHome.resolve("caches")
        if (!caches.isDirectory) return

        caches.listFiles()
            ?.filter { it.isDirectory && it.name.matches(Regex("\\d+\\.\\d+")) }
            ?.forEach { versionDir ->
                val transformsDir = versionDir.resolve("transforms")
                if (!transformsDir.isDirectory) return@forEach
                transformsDir.listFiles()?.forEach inner@{ transformDir ->
                    val transformed = transformDir.resolve("transformed")
                    if (!transformed.isDirectory) return@inner
                    transformed.listFiles()
                        ?.filter { it.name.startsWith("JetBrainsGateway") }
                        ?.forEach { gatewayDir ->
                            patchFile(gatewayDir.resolve("product-info.json"))
                        }
                }
            }
    }

    private fun patchFile(productInfo: File) {
        if (!productInfo.isFile) return
        val content = productInfo.readText()
        val patched = MISSING_CLASSPATH.replace(content) {
            "\"kind\": \"${it.groupValues[1]}\",\n      \"classPath\": []\n    }"
        }
        if (patched != content) {
            productInfo.writeText(patched)
        }
    }
}
