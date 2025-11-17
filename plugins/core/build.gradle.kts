// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("toolkit-jvm-conventions")
    id("toolkit-testing")
    id("com.gradleup.shadow") version "9.2.2"
    java
}

dependencies {
    implementation(project(":plugin-core:core"))
    implementation(project(":plugin-core:jetbrains-community"))
    implementation(project(":plugin-core:jetbrains-ultimate"))
    implementation(project(":plugin-core:resources"))
    implementation(project(":plugin-core:sdk-codegen"))
    implementation(project(":plugin-core:webview"))
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
    val coreProject = project(":plugin-core").subprojects
    coreProject.forEach {
        dependsOn(":plugin-core:${it.name}:check")
    }
}

tasks.shadowJar {
    archiveBaseName.set("plugin-core-shadow")
    archiveVersion.set(rootProject.version.toString())
    archiveClassifier.set("")

    configurations = project.configurations.runtimeClasspath.map { listOf(it) }
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
}
