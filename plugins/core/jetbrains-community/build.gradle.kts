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
    compileOnlyApi(project(":plugin-core:sdk-codegen"))
    compileOnlyApi(libs.aws.apacheClient)

    testFixturesApi(project(path = ":plugin-toolkit:core", configuration = "testArtifacts"))
    testFixturesApi(libs.mockk)
    testFixturesApi(libs.kotlin.coroutinesTest)
    testFixturesApi(libs.kotlin.coroutinesDebug)
    testFixturesApi(libs.wiremock) {
        // conflicts with transitive inclusion from docker plugin
        exclude(group = "org.apache.httpcomponents.client5")
    }

    // delete when fully split
    compileOnlyApi(project(":plugin-toolkit:core"))
    runtimeOnly(project(":plugin-toolkit:core"))
}

// fix implicit dependency on generated source
tasks.withType<Detekt> {
    dependsOn(generateTelemetry)
}

tasks.withType<DetektCreateBaselineTask> {
    dependsOn(generateTelemetry)
}

// rewrite `runtimeElements` to use the `instrumentedJar` variant
// there should never be a reason to use the default artifact at runtime, but `testFixturesRuntimeElements` pulls in `runtimeElements`
// which is causing conflict between the `runtimeElements` and `instrumentedJar` variants
configurations.runtimeElements {
    outgoing.artifacts.clear()

    outgoing.artifacts(configurations.instrumentedJar.map { it.artifacts })
    outgoing.variants {
        get("classes").apply {
            artifacts.clear()
            artifact(tasks.instrumentCode) {
                type = ArtifactTypeDefinition.JVM_CLASS_DIRECTORY
            }
        }
    }
}
