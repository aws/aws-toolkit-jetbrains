// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

dependencies {
    implementation(project(":plugin-core-q"))

    implementation(project(":plugin-amazonq:shared:jetbrains-community"))
    // hack because transform has a chat entrypoint
    implementation(project(":plugin-amazonq:chat:jetbrains-community"))
    // hack because everything references codewhisperer
    implementation(project(":plugin-amazonq:codewhisperer:jetbrains-community"))

    compileOnly(project(":plugin-core-q:jetbrains-community"))

    testImplementation(testFixtures(project(":plugin-core-q:jetbrains-community")))
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
