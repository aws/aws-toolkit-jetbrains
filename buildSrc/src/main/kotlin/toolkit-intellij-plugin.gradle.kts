// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import software.aws.toolkits.gradle.ciOnly
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension

val intellijToolkit = project.extensions.create("intellijToolkit", ToolkitIntelliJExtension::class)
// TODO: how did this break?
when {
    project.name.contains("jetbrains-rider") -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.RD)
    }

    project.name.contains("jetbrains-ultimate") -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.IU)
    }

    project.name.contains("jetbrains-gateway") -> {
        intellijToolkit.ideFlavor.set(IdeFlavor.GW)
    }

    else -> {
        // For 2025.3+, use IU as default since IC (Community) was discontinued
        intellijToolkit.ideFlavor.set(IdeFlavor.IU)
    }
}

plugins {
    id("org.jetbrains.intellij.platform.module")
}

intellijPlatform {
    instrumentCode = false
}

// CI keeps running out of RAM, so limit IDE instance count to 4
ciOnly {
    abstract class NoopBuildService : BuildService<BuildServiceParameters.None> {}
    val noopService = gradle.sharedServices.registerIfAbsent("noopService", NoopBuildService::class.java) {
        maxParallelUsages = 2
    }

    tasks.matching { it is Test || it is SandboxAware }.configureEach {
        usesService(noopService)
    }
}
