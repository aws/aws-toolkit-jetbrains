// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("toolkit-jvm-conventions")
    id("toolkit-testing")
    alias(libs.plugins.gradleup.shadow)
}

dependencies {
    implementation(project(":plugin-core-q:core-q"))
    implementation(project(":plugin-core-q:jetbrains-community"))
    implementation(project(":plugin-core-q:jetbrains-ultimate"))
    implementation(project(":plugin-core-q:resources"))
    implementation(project(":plugin-core-q:sdk-codegen"))
    implementation(project(":plugin-core-q:webview"))
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.jdk14)
}

configurations {
    configureEach {
        // IDE provides netty
        exclude("io.netty")
    }

    // Make sure we exclude stuff we either A) ships with IDE, B) we don't use to cut down on size
    runtimeClasspath {
        exclude(group = "com.google.code.gson")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
}

tasks.check {
    val coreProject = project(":plugin-core-q").subprojects
    coreProject.forEach {
        dependsOn(":plugin-core-q:${it.name}:check")
    }
}
