// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import software.aws.toolkits.gradle.jvmTarget
import software.aws.toolkits.gradle.kotlinTarget

plugins {
    id("java")
    kotlin("jvm")
}

val javaVersion = project.jvmTarget().get()
java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("21")
        languageVersion = KotlinVersion.fromVersion(project.kotlinTarget().get())
        apiVersion = KotlinVersion.fromVersion(project.kotlinTarget().get())
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}
