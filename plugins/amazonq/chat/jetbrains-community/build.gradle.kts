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
    implementation(project(path = ":plugin-core", configuration = "shadow"))

    implementation(project(":plugin-amazonq:shared:jetbrains-community"))
    // everything references codewhisperer, which is not ideal
    implementation(project(":plugin-amazonq:codewhisperer:jetbrains-community"))
    implementation(libs.diff.util)
    implementation(libs.commons.text)

    compileOnly(project(":plugin-core:jetbrains-community"))

    testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
