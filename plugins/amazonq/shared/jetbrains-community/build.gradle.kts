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
    intellijPlatform {
        localPlugin(project(":plugin-core"))
    }

    compileOnlyApi(project(":plugin-core:jetbrains-community"))

    // CodeWhispererTelemetryService uses a CircularFifoQueue
    implementation(libs.commons.collections)
    implementation(libs.nimbus.jose.jwt)

    testFixturesApi(testFixtures(project(":plugin-core:jetbrains-community")))
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    intoChild(intellijPlatform.projectName.map { "$it/lib" })
        .from(pluginXmlJar)
}
