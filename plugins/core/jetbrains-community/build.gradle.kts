// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.telemetry.generator.gradle.GenerateTelemetry

plugins {
    id("java-library")
    id("toolkit-testing")
    id("toolkit-intellij-subplugin")
}

buildscript {
    dependencies {
        classpath(libs.telemetryGenerator)
    }
}

sourceSets {
    main {
        java.srcDir(project.layout.buildDirectory.dir("generated-src"))
    }
}

val generateTelemetry = tasks.register<GenerateTelemetry>("generateTelemetry") {
    inputFiles = listOf(file("${project.projectDir}/resources/telemetryOverride.json"))
    outputDirectory = project.layout.buildDirectory.dir("generated-src").get().asFile
}

tasks.compileKotlin {
    dependsOn(generateTelemetry)
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

dependencies {
    compileOnlyApi(project(":plugin-core:core"))
    compileOnlyApi(libs.aws.apacheClient)
    compileOnlyApi(libs.aws.nettyClient)

    api(libs.aws.iam)

    testFixturesApi(project(path = ":plugin-core:core", configuration = "testArtifacts"))
    testFixturesApi(libs.wiremock) {
        // conflicts with transitive inclusion from docker plugin
        exclude(group = "org.apache.httpcomponents.client5")
    }
}

// fix implicit dependency on generated source
tasks.withType<Detekt> {
    dependsOn(generateTelemetry)
}

tasks.withType<DetektCreateBaselineTask> {
    dependsOn(generateTelemetry)
}
