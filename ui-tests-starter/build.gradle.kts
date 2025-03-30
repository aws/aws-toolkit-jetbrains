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
val testPlugins by configurations.registering

sourceSets {
    test {
        java.setSrcDirs(findFolders(project, "tst-prep", ideProfile))
        resources.setSrcDirs(findFolders(project, "tst-resources", ideProfile))
    }
}

val uiTestSource = sourceSets.create("uiTest") {
    java.setSrcDirs(findFolders(project, "tst", ideProfile))
}

idea {
    module {
        testSources.from(uiTestSource.allSource.srcDirs)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

val uiTestImplementation by configurations.getting
val uiTestRuntimeOnly by configurations.getting

configurations.getByName(uiTestSource.compileClasspathConfigurationName) {
    extendsFrom(uiTestImplementation)
}

configurations.getByName(uiTestSource.runtimeClasspathConfigurationName) {
    extendsFrom(uiTestImplementation)
}

dependencies {
    // should really be set by the BOM, but too much work to figure out right now
    uiTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    uiTestImplementation(platform(libs.junit5.bom))
    uiTestImplementation(libs.junit5.jupiter)

    // not sure why not coming in transitively for starter
    uiTestRuntimeOnly(libs.kotlin.coroutines)

    intellijPlatform {
        val version = ideProfile.community.sdkVersion
        intellijIdeaCommunity(version, !version.contains("SNAPSHOT"))

        localPlugin(project(":plugin-core"))
        testImplementation(project(":plugin-core:core"))
        testImplementation(project(":plugin-core:jetbrains-community"))
        testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))

        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.JUnit5)

        testFramework(TestFrameworkType.Starter, configurationName = uiTestImplementation.name)
    }

    testPlugins(project(":plugin-amazonq", "pluginZip"))
    testPlugins(project(":plugin-core", "pluginZip"))
}

tasks.test {
    enabled = false
}

val prepareAmazonQTest by intellijPlatformTesting.testIde.registering {
    task {
        useJUnitPlatform()
    }
}

tasks.register<Test>("uiTest") {
    testClassesDirs = uiTestSource.output.classesDirs
    classpath = uiTestSource.runtimeClasspath

    dependsOn(prepareAmazonQTest)
    dependsOn(testPlugins)

    systemProperty("ui.test.plugins", testPlugins.get().asPath)
    systemProperty("org.gradle.project.ideProfileName", ideProfile.name)
    val testSuite = System.getenv("TEST_DIR") ?: ""
    filter {
        "includeTestsMatching(software.aws.toolkits.jetbrains.uitests.testTests.*)"
    }
}

// hack to disable ui tests in ./gradlew check
val action = Action<TaskExecutionGraph> {
    if (hasTask(tasks.check.get())) {
        tasks.test.get().enabled = false
    }
}
gradle.taskGraph.whenReady(action)
