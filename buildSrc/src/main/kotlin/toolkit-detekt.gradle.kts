// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import io.gitlab.arturbosch.detekt.Detekt

val detektVersion: String by project

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("toolkit-testing")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
    detektPlugins(project(":detekt-rules"))
}

detekt {
    input.from("$projectDir")
    buildUponDefaultConfig = false
    parallel = true
    allRules = false
    config = files("$rootDir/detekt-rules/detekt.yml")

    reports {
        html.enabled = true // observe findings in your browser with structure and code snippets
        xml.enabled = true // checkstyle like format mainly for integrations like Jenkins
        sarif.enabled = true // standardized SARIF format to support integrations with Github Code Scanning
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
    dependsOn(":detekt-rules:assemble")
}

tasks.check {
    dependsOn(tasks.detekt)
}
