// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

dependencyResolutionManagement {
    versionCatalogs {
        maybeCreate("libs").apply {
            // pull value from IJ library list: https://github.com/JetBrains/intellij-community/blob/<mv>/.idea/libraries/kotlinx_coroutines_jdk8.xml
            //                              or: https://github.com/JetBrains/intellij-community/blob/<mv>/.idea/libraries/kotlinx_coroutines_core.xml
            val version = when (providers.gradleProperty("ideProfileName").getOrNull() ?: return@apply) {
                "2023.3", "2024.1" -> {
                    "1.7.3"
                }

                "2024.2" -> {
                    "1.8.0"
                }

                else -> { error("not set") }
            }

            version("kotlinxCoroutines", version)
        }
    }
}
