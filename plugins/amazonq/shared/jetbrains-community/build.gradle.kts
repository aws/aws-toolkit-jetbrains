// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

intellij {
    plugins.add(project(":plugin-core"))
}

dependencies {
    compileOnlyApi(project(":plugin-core:jetbrains-community"))

    // CodeWhispererTelemetryService uses a CircularFifoQueue
    implementation(libs.commons.collections)

    testFixturesApi(testFixtures(project(":plugin-core:jetbrains-community")))
}
