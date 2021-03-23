// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

val mockitoVersion: String by project
val mockitoKotlinVersion: String by project
val assertjVersion: String by project

plugins {
    id("org.jetbrains.intellij")
}

tasks.withType<Test>().all {
//    systemProperty("log.dir", "${(project.extensions["intellij"] as org.jetbrains.intellij.IntelliJPluginExtension).sandboxDirectory}-test/logs")
}
