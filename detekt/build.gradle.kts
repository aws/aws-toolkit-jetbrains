// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

val kotlinVersion: String by project
val detektVersion: String by project

plugins {
    id("toolkit-kotlin-conventions")
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    implementation("io.gitlab.arturbosch.detekt:detekt-api:$detektVersion")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:$detektVersion")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
}

detekt {
    buildUponDefaultConfig = false
    allRules = false
    config = files("$rootDir/buildSrc/detekt/detekt.yml")

    reports {
        html.enabled = true // observe findings in your browser with structure and code snippets
        xml.enabled = true // checkstyle like format mainly for integrations like Jenkins
        sarif.enabled = true // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations with Github Code Scanning
    }
}

tasks.check {
    dependsOn(tasks.detekt)
}
