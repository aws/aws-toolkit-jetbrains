// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

dependencyResolutionManagement {
    versionCatalogs {
        maybeCreate("libs").apply {
            val ideProfileName = providers.gradleProperty("ideProfileName").getOrNull() ?: return@apply

            // pull value from IJ library list: https://github.com/JetBrains/intellij-community/blob/<mv>/.idea/libraries/kotlinx_coroutines_core.xml
            val coroutinesVersion = when (ideProfileName) {
                "2026.1", "2026.2" -> {
                    "1.10.2-intellij-1"
                }

                "2026.3" -> {
                    "1.11.0-intellij-1"
                }

                else -> { error("not set") }
            }

            version("kotlinxCoroutines", coroutinesVersion)

            // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#bundled-stdlib-versions
            // used to align non-compiler kotlin dependencies (stdlib, reflect) with the IDE's bundled version
            val kotlinStdlibVersion = when (ideProfileName) {
                "2026.1" -> "2.3.20"
                "2026.2" -> "2.4.0"
                // 2026.3 GA still bundles an RC stdlib
                "2026.3" -> "2.4.21-RC"
                else -> { error("not set") }
            }

            version("kotlinStdlib", kotlinStdlibVersion)
        }
    }
}
