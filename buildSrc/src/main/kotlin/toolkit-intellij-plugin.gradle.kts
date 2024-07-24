// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import software.aws.toolkits.gradle.ciOnly
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension
import java.net.URI

private val toolkitIntelliJ = project.extensions.create<ToolkitIntelliJExtension>("intellijToolkit")

plugins {
    id("org.jetbrains.intellij.platform.module")
}

intellijPlatform {
    instrumentCode = false
}

// there is an issue if this is declared more than once in a project (either directly or through script plugins)
repositories {
    intellijPlatform {
        jetbrainsIdeInstallers()
        localPlatformArtifacts()
        intellijDependencies { url = URI(Constants.Locations.INTELLIJ_DEPENDENCIES_REPOSITORY) }
        releases { url = URI(Constants.Locations.INTELLIJ_REPOSITORY_RELEASES) }
        snapshots { url = URI(Constants.Locations.INTELLIJ_REPOSITORY_SNAPSHOTS) }
        marketplace { url = URI("https://dtahfujkndrht.cloudfront.net/plugins.jetbrains.com/maven/") }
        jetbrainsRuntime { url = URI("https://d2xrhe97vsfxuc.cloudfront.net") }
    }
}

dependencies {
    intellijPlatform {
        instrumentationTools()
    }
}

// CI keeps running out of RAM, so limit IDE instance count to 4
ciOnly {
    abstract class NoopBuildService : BuildService<BuildServiceParameters.None> {}
    val noopService = gradle.sharedServices.registerIfAbsent("noopService", NoopBuildService::class.java) {
        maxParallelUsages = 4
    }

    tasks.matching { it is TestableAware }.configureEach {
        usesService(noopService)
    }
}
