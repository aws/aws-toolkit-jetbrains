// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
}

dependencies {
    // dev.detekt:detekt-test's module metadata requests the 'detekt-api-test-fixtures' capability, whose binary jar
    // was never published for 2.0.0-alpha.5 (only -sources.jar) - a confirmed upstream bug:
    // https://github.com/detekt/detekt/issues/9409, fixed by https://github.com/detekt/detekt/pull/9439 but not yet
    // released in a new alpha. Strip that broken dependency and supply a normal detekt-api ourselves instead.
    // Remove this rule once a detekt release includes the fix.
    components {
        all {
            if (id.group == "dev.detekt" && id.module.name == "detekt-test") {
                allVariants {
                    withDependencies {
                        removeAll { it.group == "dev.detekt" && it.name == "detekt-api" }
                    }
                }
            }
        }
    }

    compileOnly(libs.detekt.api)
    compileOnly(libs.detekt.kotlinAnalysisApi)

    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.detekt.testJunit)
    testImplementation(libs.junit4)
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.assertj)

    // only used to make test work
    testRuntimeOnly(libs.slf4j.api)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.WARN
}
