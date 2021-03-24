// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension

val toolkitIntelliJ = project.extensions.create<ToolkitIntelliJExtension>("intellijToolkit")
val ideProfile = IdeVersions.ideProfile(project)

plugins {
    id("toolkit-kotlin-conventions")
    id("org.jetbrains.intellij")
}

println(toolkitIntelliJ.ideFlavor)

toolkitIntelliJ.ideFlavor.map {
    println("WTF WTF")
    intellij {
        val productProfile = when(it) {
            ToolkitIntelliJExtension.IdeFlavor.IC -> ideProfile.community
            ToolkitIntelliJExtension.IdeFlavor.IU -> ideProfile.ultimate
            ToolkitIntelliJExtension.IdeFlavor.RD -> ideProfile.rider
            else -> throw UnsupportedOperationException("$it")
        }

        pluginName = "aws-toolkit-jetbrains"
        version = productProfile.sdkVersion

        setPlugins(*productProfile.plugins)
    }
}

sourceSets {
    main {
        java.srcDirs(findFolders(project, "src", ideProfile))
        resources.srcDirs(findFolders(project, "resources", ideProfile))
    }
    test {
        java.srcDirs(findFolders(project, "tst", ideProfile))
        resources.srcDirs(findFolders(project, "tst-resources", ideProfile))
    }

    plugins.withType<ToolkitIntegrationTestingPlugin> {
        maybeCreate("integrationTest").apply {
            java.srcDirs(findFolders(project, "it", ideProfile))
            resources.srcDirs(findFolders(project, "it-resources", ideProfile))
        }
    }
}

tasks.patchPluginXml {
    setSinceBuild(ideProfile.sinceVersion)
    setUntilBuild(ideProfile.untilVersion)
}

tasks.buildSearchableOptions {
    enabled = false
}

tasks.withType<Test>().all {
    systemProperty("log.dir", "${intellij.sandboxDirectory}-test/logs")
    systemProperty("testDataPath", file("testdata").absolutePath)
}
