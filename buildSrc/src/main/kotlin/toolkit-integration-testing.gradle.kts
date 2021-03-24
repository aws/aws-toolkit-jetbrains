import gradle.kotlin.dsl.accessors._17b5bb42cd83a5b395e515786e8b59f8.sourceSets
import gradle.kotlin.dsl.accessors._17b5bb42cd83a5b395e515786e8b59f8.test

// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    java
    idea
    id("toolkit-testing")
}

val integrationTests: SourceSet = sourceSets.maybeCreate("integrationTest")
sourceSets {
    integrationTests.apply {
        java.setSrcDirs(listOf("it"))
        resources.srcDirs(listOf("it-resources"))

        compileClasspath += main.get().output + test.get().output
        runtimeClasspath += main.get().output + test.get().output
    }
}

configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME))
    isCanBeResolved = true
}
configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME))
    isCanBeResolved = true
}

// Add the integration test source set to test jar
tasks.named<Jar>("testJar") {
    from(sourceSets.getByName("integrationTest").output)
}

idea {
    module {
        testSourceDirs = testSourceDirs + integrationTests.java.srcDirs
        testResourceDirs = testResourceDirs + integrationTests.resources.srcDirs
    }
}

tasks.register<Test>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the integration tests."
    testClassesDirs = integrationTests.output.classesDirs
    classpath = integrationTests.runtimeClasspath

    mustRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(integrationTests.compileJavaTaskName)
}
