// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import software.aws.toolkits.gradle.jvmTarget

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("toolkit-testing")
}

// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    detektPlugins(versionCatalog.findLibrary("detekt-formattingRules").get())
    detektPlugins(project(":detekt-rules"))
}

detekt {
    val rulesProject = project(":detekt-rules").projectDir
    source.setFrom("$projectDir")
    buildUponDefaultConfig = true
    parallel = true
    allRules = false
    config = files("$rulesProject/detekt.yml")
    autoCorrect = true
}

tasks.withType<Detekt> {
    jvmTarget = project.jvmTarget().get().majorVersion
    include("**/*.kt")

    reports {
        html.required.set(true) // Human readable report
        xml.required.set(true) // Checkstyle like format for CI tool integrations
    }
}

tasks.withType<DetektCreateBaselineTask> {
    jvmTarget = project.jvmTarget().get().majorVersion
    // weird issue where the baseline tasks can't find the source code
    source = files("$projectDir").asFileTree
    include("**/*.kt")
}
