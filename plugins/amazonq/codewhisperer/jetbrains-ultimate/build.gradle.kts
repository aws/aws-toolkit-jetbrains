// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IU)
}

dependencies {
    implementation(project(":plugin-core-q"))

    compileOnly(project(":plugin-amazonq:codewhisperer:jetbrains-community"))
    compileOnly(project(":plugin-amazonq:shared:jetbrains-ultimate"))

    compileOnly(project(":plugin-core-q:jetbrains-ultimate"))
    testCompileOnly(project(":plugin-core-q:jetbrains-ultimate"))

    testImplementation(testFixtures(project(":plugin-amazonq:codewhisperer:jetbrains-community")))
    testImplementation(testFixtures(project(":plugin-core-q:jetbrains-ultimate")))
    testImplementation(project(path = ":plugin-toolkit:jetbrains-ultimate", configuration = "testArtifacts"))
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
