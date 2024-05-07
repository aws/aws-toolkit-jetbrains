// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("java-library")
    id("toolkit-publishing-conventions")
    id("toolkit-patch-plugin-xml-conventions")
    id("toolkit-jvm-conventions")
}

dependencies {
    api(project(":plugin-core:core"))
    api(project(":plugin-core:sdk-codegen"))
    api(project(":plugin-core:jetbrains-community"))
    implementation(project(":plugin-core:resources"))
    implementation(project(":plugin-core:jetbrains-ultimate"))
    implementation(project(":plugin-core:webview"))
}

tasks.check {
    val coreProject = project(":plugin-core").subprojects
    coreProject.forEach {
        dependsOn(":plugin-core:${it.name}:check")
    }
}
