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

    compileOnly(project(":plugin-core-q:jetbrains-community"))

    implementation(project(":plugin-amazonq:shared:jetbrains-community"))
    implementation(libs.lsp4j)
    // CodeWhispererTelemetryService uses a CircularFifoQueue, previously transitive from zjsonpatch
    implementation(libs.commons.collections)

    testFixturesApi(testFixtures(project(":plugin-core-q:jetbrains-community")))
    testFixturesApi(project(path = ":plugin-toolkit:jetbrains-core", configuration = "testArtifacts"))
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
