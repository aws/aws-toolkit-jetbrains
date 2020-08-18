// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

    val kotlinVersion: String by project
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

repositories {
    maven("https://plugins.gradle.org/m2/")
    mavenLocal()
    mavenCentral()
    jcenter()
}

plugins {
    // TODO this really doesn't work. The plugin block requires a const string but the above
    // hack we had in place to copy the properties also fixes this for now.
    val kotlinVersion: String by project
    kotlin("jvm") version kotlinVersion
    `java-gradle-plugin`
}

sourceSets {
    main {
        java.srcDir("src")
    }
    test {
        java.srcDir("src")
    }
}

dependencies {
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    api("org.eclipse.jgit:org.eclipse.jgit:5.0.2.201807311906-r")
    api("com.atlassian.commonmark:commonmark:0.11.0")
    api("software.amazon.awssdk:codegen:$awsSdkVersion")

    implementation("gradle.plugin.org.jetbrains.intellij.plugins:gradle-intellij-plugin:$ideaPluginVersion")

    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("junit:junit:$junitVersion")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}

gradlePlugin {
    plugins {
        create("changeLog") {
            id = "toolkit-change-log"
            implementationClass = "software.aws.toolkits.gradle.changelog.ChangeLogPlugin"
        }
    }
}
