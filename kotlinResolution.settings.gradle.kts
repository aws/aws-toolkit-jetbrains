// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

dependencyResolutionManagement {
    versionCatalogs {
        maybeCreate("libs").apply {
            val ideProfileName = providers.gradleProperty("ideProfileName").getOrNull() ?: return@apply

            // pull value from IJ library list: https://github.com/JetBrains/intellij-community/blob/<mv>/.idea/libraries/kotlinx_coroutines_core.xml
            val coroutinesVersion = when (ideProfileName) {
                "2025.1" -> {
                    "1.8.0-intellij-11"
                }

                "2025.2", "2025.3" -> {
                    "1.10.1-intellij-5"
                }

                "2026.1" -> {
                    "1.10.2-intellij-1"
                }

                else -> { error("not set") }
            }

            version("kotlinxCoroutines", coroutinesVersion)

            // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#bundled-stdlib-versions
            // used to align non-compiler kotlin dependencies (stdlib, reflect) with the IDE's bundled version
            val kotlinStdlibVersion = when (ideProfileName) {
                "2025.1" -> "2.1.10"
                "2025.2" -> "2.1.20"
                "2025.3" -> "2.2.20"
                "2026.1" -> "2.3.20"
                else -> { error("not set") }
            }

            version("kotlinStdlib", kotlinStdlibVersion)
        }
    }
}
