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

idea {
    module {
        testSourceDirs.plusAssign(integrationTests.java.srcDirs)
//        testResourceDirs += integrationTests.resources.srcDirs
//        scopes.TEST.plus += [ configurations.named(integrationTests.compileClasspathConfigurationName) ]
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the integration tests."
    testClassesDirs = integrationTests.output.classesDirs
    classpath = integrationTests.runtimeClasspath

    // TODO: Move this
    project.plugins.withId("org.jetbrains.intellij") {
        systemProperty("log.dir", "${(project.extensions["intellij"] as org.jetbrains.intellij.IntelliJPluginExtension).sandboxDirectory}-test/logs")
    }

    // TODO: Move this
    systemProperty("testDataPath", project.rootDir.toPath().resolve("testdata").toString())

    mustRunAfter(tasks.named("test"))
}

//tasks.named("build") {
//    dependsOn(integrationTest)
//}
