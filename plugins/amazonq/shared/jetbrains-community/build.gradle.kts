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

    // FIX_WHEN_MIN_IS_242
    if (providers.gradleProperty("ideProfileName").get() == "241") {
        implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    }

    testFixturesApi(testFixtures(project(":plugin-core:jetbrains-community")))
}
