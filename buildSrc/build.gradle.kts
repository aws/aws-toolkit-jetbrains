// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

val jacksonVersion: String by project
val kotlinVersion: String by project
val awsSdkVersion: String by project

val assertjVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project
val mockitoKotlinVersion: String by project
val ideaPluginVersion: String by project

buildscript {
    // This has to be here otherwise properties are not loaded and nothing works
    val props = java.util.Properties()
    file("${project.projectDir.parent}/gradle.properties").inputStream().use { props.load(it) }
    props.entries.forEach { it: Map.Entry<Any, Any> -> project.extensions.add(it.key.toString(), it.value) }
}

plugins {
    `kotlin-dsl`
}

// Note: We can't use our standard source layout due to https://github.com/gradle/gradle/issues/14310

dependencies {
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.0.2.201807311906-r")
    implementation("org.commonmark:commonmark:0.17.1")

    implementation("software.amazon.awssdk:codegen:$awsSdkVersion")

    implementation("org.jetbrains.intellij.plugins:gradle-intellij-plugin:$ideaPluginVersion")

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

    implementation("org.gradle:test-retry-gradle-plugin:1.2.1")
    implementation("com.adarshr:gradle-test-logger-plugin:2.1.1")

    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("junit:junit:$junitVersion")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
