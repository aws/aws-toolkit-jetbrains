// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-intellij-plugin")

    id("org.jetbrains.intellij.platform")
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

val testPlugins by configurations.registering

dependencies {
    //testImplementation(platform("com.jetbrains.intellij.tools:ide-starter"))
    // should really be set by the BOM, but too much work to figure out right now
    testImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    testImplementation(project(":plugin-core:jetbrains-community"))
    testImplementation(project(":plugin-core:core"))
    testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))





    intellijPlatform {
        intellijIdeaCommunity(IdeVersions.ideProfile(providers).map { it.name })
        intellijIdeaCommunity(ideProfile.community.sdkVersion)

        testFramework(TestFrameworkType.JUnit5)
        testFramework(TestFrameworkType.Starter)
        testFramework(TestFrameworkType.Bundled)
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
