// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.ciOnly

val mockitoVersion: String by project
val mockitoKotlinVersion: String by project
val assertjVersion: String by project

plugins {
    id("java") // Need for jacoco
    id("jacoco")
    id("org.gradle.test-retry")
    id("com.adarshr.test-logger")
}

dependencies {
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")

    // We don't dictate junit vs testng vs junit5 here since it's not consistent
}

// TODO: Can we model this using https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures
val testArtifacts by configurations.creating
val testJar = tasks.register<Jar>("testJar") {
    archiveBaseName.set("${project.name}-test")
    from(sourceSets.test.get().output)
    from(sourceSets.getByName("integrationTest").output)
}

artifacts {
    add("testArtifacts", testJar)
}

tasks.withType<Test>().all {
    ciOnly {
        retry {
            failOnPassedAfterRetry.set(false)
            maxFailures.set(5)
            maxRetries.set(2)
        }
    }

    reports {
        junitXml.isEnabled = true
        html.isEnabled = true
    }

    testlogger {
        showFullStackTraces = true
        showStandardStreams = true
        showPassedStandardStreams = false
        showSkippedStandardStreams = true
        showFailedStandardStreams = true
    }

    configure<JacocoTaskExtension> {
        // don't instrument sdk, icons, ktlint, etc.
        includes = listOf("software.aws.toolkits.*")
        excludes = listOf("software.aws.toolkits.ktlint.*")
    }
}

// Do not generate reports for individual projects
tasks.jacocoTestReport.configure {
    enabled = false
}
