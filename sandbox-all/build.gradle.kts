// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

plugins {
    id("toolkit-intellij-plugin")
    id("org.jetbrains.intellij")
}

toolkitIntelliJ.apply {
    val runIdeVariant = providers.gradleProperty("runIdeVariant")
    ideFlavor.set(IdeFlavor.values().firstOrNull { it.name == runIdeVariant.orNull } ?: IdeFlavor.IC)
}

intellij {
    val ideProfile = IdeVersions.ideProfile(project)
    version.set(ideProfile.community.version())
    localPath.set(ideProfile.community.localPath())
    plugins.set(
        listOf(
            project(":plugin-core"),
            project(":plugin-amazonq"),
            project(":plugin-toolkit:intellij-standalone"),
        )
    )

    updateSinceUntilBuild.set(false)
    instrumentCode.set(false)
}
