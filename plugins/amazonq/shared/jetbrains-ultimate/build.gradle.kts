// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IU)
}

dependencies {
    intellijPlatform {
        // RD platform is only available in 2025.3 and later
        when (providers.gradleProperty("ideProfileName").get()) {
            "2025.2", "2025.3" -> {
                bundledModule("intellij.rd.platform")
            }
        }
    }
    compileOnly(project(":plugin-amazonq:shared:jetbrains-community"))
    compileOnly(project(":plugin-core:jetbrains-ultimate"))

    testFixturesApi(testFixtures(project(":plugin-core:jetbrains-community")))
    testFixturesApi(testFixtures(project(":plugin-core:jetbrains-ultimate")))
}
