// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("java")
    id("idea")
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
val testJar = tasks.named<Jar>("testJar") {
    from(integrationTests.output)
}

// Silly but allows higher throughput of the build because we can start compiling / testing other modules while the tests run
// This works because the sourceSet 'integrationTest' extends 'test', so it won't compile until after 'test' is compiled, but the
// task graph goes 'compileTest*' -> 'test' -> 'compileIntegrationTest*' -> 'testJar'.
// By flipping the order of the graph slightly, we can unblock downstream consumers of the testJar to start running tasks while this project
// can be executing the 'test' task.
tasks.test {
    mustRunAfter(testJar)
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

    mustRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTests)
}
