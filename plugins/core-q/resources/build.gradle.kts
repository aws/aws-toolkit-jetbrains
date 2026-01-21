// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import de.undercouch.gradle.tasks.download.Download
import software.aws.toolkits.gradle.resources.ValidateMessages

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("de.undercouch.download")
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("downloaded-resources"))
    }
}

dependencies {
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit5.jupiterVintage)
}

tasks.test {
    useJUnitPlatform()
}

val download = tasks.register<Download>("downloadResources") {
    val resourcesDir = layout.buildDirectory.dir("downloaded-resources/software/aws/toolkits/resources/").get().asFile
    dest(resourcesDir)
    src(listOf("https://idetoolkits.amazonwebservices.com/endpoints.json"))
    onlyIfModified(true)
    useETag(true)
    doFirst {
        mkdir(resourcesDir)
    }
}

tasks.processResources {
    dependsOn(download)
}

val validateLocalizedMessages = tasks.register<ValidateMessages>("validateLocalizedMessages") {
    paths.from("resources/software/amazon/q/resources/MessagesBundle.properties")
}

tasks.check {
    dependsOn(validateLocalizedMessages)
}
