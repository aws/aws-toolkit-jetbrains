// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-intellij-plugin")

    id("org.jetbrains.intellij.platform.base")
}

val ideProfile = IdeVersions.ideProfile(project)

// Add our source sets per IDE profile version (i.e. src-211)
sourceSets {
    test {
        java.srcDirs(findFolders(project, "tst", ideProfile))
        resources.srcDirs(findFolders(project, "tst-resources", ideProfile))
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

tasks.initializeIntellijPlatformPlugin {
    enabled = false
}

tasks.verifyPluginProjectConfiguration {
    runtimeDirectory.set(null as File?)
    enabled = false
}

val testPlugins by configurations.registering

dependencies {
    // should really be set by the BOM, but too much work to figure out right now
    testImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    intellijPlatform {
        // shouldn't be needed? but IsolationException
        val version = ideProfile.community.sdkVersion
        intellijIdeaCommunity(version, !version.contains("SNAPSHOT"))
        testFramework(TestFrameworkType.Starter)
    }

    testPlugins(project(":plugin-amazonq", "pluginZip"))
    testPlugins(project(":plugin-core", "pluginZip"))
}

tasks.test {
    dependsOn(testPlugins)

    useJUnitPlatform()

    systemProperty("ui.test.plugins", testPlugins.get().asPath)
}

// hack to disable ui tests in ./gradlew check
val action = Action<TaskExecutionGraph> {
    if (hasTask(tasks.check.get())) {
        tasks.test.get().enabled = false
    }
}
gradle.taskGraph.whenReady(action)
