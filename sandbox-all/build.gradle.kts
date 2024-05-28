// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

plugins {
    id("toolkit-intellij-plugin")
    id("org.jetbrains.intellij.platform")
}

toolkitIntelliJ.apply {
    val runIdeVariant = providers.gradleProperty("runIdeVariant")
    ideFlavor.set(IdeFlavor.values().firstOrNull { it.name == runIdeVariant.orNull } ?: IdeFlavor.IC)
}

tasks.buildPlugin {
    doFirst {
        throw GradleException("This project does not produce an artifact. Use project-specific command, e.g. :plugin-toolkit:intellij-standalone:runIde")
    }
}
