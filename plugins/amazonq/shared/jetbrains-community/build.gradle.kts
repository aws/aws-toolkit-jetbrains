// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.models.Coordinates
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

val ideProfile = IdeVersions.ideProfile(project)

dependencies {
    intellijPlatform {
        platformDependency(Coordinates(groupId = "com.jetbrains.intellij.rd", artifactId = "rd-platform"))
    }

    implementation(project(":plugin-core-q"))

    compileOnlyApi(project(":plugin-core-q:jetbrains-community"))

    // CodeWhispererTelemetryService uses a CircularFifoQueue
    implementation(libs.commons.collections)
    implementation(libs.nimbus.jose.jwt)
    api(libs.lsp4j)

    testFixturesApi(testFixtures(project(":plugin-core-q:jetbrains-community")))

    testImplementation(project(":plugin-core-q:jetbrains-community"))
}

sourceSets {
    test {
        java.srcDirs(
            findFolders(project(":plugin-core-q:jetbrains-community").project, "tst", ideProfile).map {
                project(":plugin-core-q:jetbrains-community").project.file(it)
            }
        )
        resources.srcDirs(
            findFolders(project(":plugin-core-q:jetbrains-community").project, "tst-resources", ideProfile)
                .map {
                    project(":plugin-core-q:jetbrains-community").project.file(it)
                }
        )
    }
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
