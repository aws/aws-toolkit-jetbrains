// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

plugins {
    // we're not publishing anything, but convenient to have the IDE variant selection and sandbox setup logic
    id("toolkit-publish-root-conventions")
}

tasks.verifyPlugin {
    isEnabled = false
}

tasks.buildPlugin {
    doFirst {
        throw StopActionException("This project does not produce an artifact. Use project-specific command, e.g. :plugin-toolkit:intellij-standalone:runIde")
    }
}

intellijPlatform {
    buildSearchableOptions.set(false)
}

dependencies {
    intellijPlatform {
        localPlugin(project(":plugin-core"))
        localPlugin(project(":plugin-amazonq"))
        localPlugin(project(":plugin-toolkit:intellij-standalone"))
    }
}
