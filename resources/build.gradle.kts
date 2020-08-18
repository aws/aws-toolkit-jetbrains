// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import de.undercouch.gradle.tasks.download.Download

plugins {
    id("de.undercouch.download")
}

sourceSets {
    main {
        resources.srcDir("$buildDir/downloaded-resources")
    }
}

val download = tasks.register<Download>("downloadResources") {
    dest("$buildDir/downloaded-resources/software/aws/toolkits/resources/")
    src(listOf("https://idetoolkits.amazonwebservices.com/endpoints.json"))
    onlyIfModified(true)
    useETag(true)
    doFirst {
        mkdir("$buildDir/downloaded-resources/software/aws/toolkits/resources/")
    }
}

tasks["processResources"].dependsOn(download)
