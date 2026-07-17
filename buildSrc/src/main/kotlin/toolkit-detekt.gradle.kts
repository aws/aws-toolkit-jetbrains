// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import software.aws.toolkits.gradle.jvmTarget

plugins {
    id("dev.detekt")
    id("toolkit-testing")
}

// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    detektPlugins(versionCatalog.findLibrary("detekt-formattingRules").get())
    detektPlugins(project(":detekt-rules"))
}

// detekt with type introspection configured in kotlin conventions
private val detektFiles = fileTree(projectDir).matching {
    include("**/*.kt", "**/*.kts")
    exclude("**/build")
}

detekt {
    val rulesProject = project(":detekt-rules").projectDir
    source.setFrom(detektFiles)
    buildUponDefaultConfig = true
    parallel = true
    allRules = false
    config.setFrom("$rulesProject/detekt.yml")
    autoCorrect = true
    // grandfathers in pre-existing findings surfaced by detekt 2.0's more accurate K2-based analysis;
    // new violations in new/changed code still fail the build
    baseline.set(project.file("detekt-baseline.xml"))
}

val javaVersion = project.jvmTarget().get()

tasks.withType<Detekt>().configureEach {
    jvmTarget = javaVersion.majorVersion
    dependsOn(":detekt-rules:assemble")

    reports {
        html.required.set(true) // Human readable report
        checkstyle.required.set(true) // Checkstyle like format for CI tool integrations
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = javaVersion.majorVersion
    dependsOn(":detekt-rules:assemble")

    // weird issue where the baseline tasks can't find the source code
    source.plus(detektFiles)
}
