// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("java-library")
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("toolkit-intellij-subplugin")
    id("toolkit-integration-testing")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IU)
}

dependencies {
    intellijPlatform {
        localPlugin(project(":plugin-core"))
        
        // Gateway API (core plugin + modules)
        bundledPlugin("com.jetbrains.gateway")
        bundledModule("intellij.platform.collaborationTools.auth.base")
        
        // Gateway modules found in 2025.3
        bundledModule("intellij.gateway.station")
        
        // RD platform module found in 2025.3
        bundledModule("intellij.rd.platform")
        
        // Go Plugin (as Marketplace dependency with specific version)
        plugin("org.jetbrains.plugins.go:253.27642.30")
        
        // JavaScript Debugging Suite
        bundledPlugin("JavaScript")
        bundledPlugin("JavaScriptDebugger")
        bundledPlugin("NodeJS")
    }
    compileOnlyApi(project(":plugin-toolkit:jetbrains-core"))
    compileOnlyApi(project(":plugin-core:jetbrains-ultimate"))

    testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))
    testImplementation(project(":plugin-toolkit:jetbrains-core"))
    testImplementation(project(path = ":plugin-toolkit:jetbrains-core", configuration = "testArtifacts"))
    testImplementation(project(path = ":plugin-core:core", configuration = "testArtifacts"))
    testImplementation(libs.mockk)

    // delete when fully split
    testRuntimeOnly(project(":plugin-core:jetbrains-ultimate"))
}
