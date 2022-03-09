// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.ciOnly

plugins {
    id("java") // Needed for referencing "implementation" configuration
    id("jacoco")
    id("org.gradle.test-retry")
    id("com.adarshr.test-logger")
}

// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    testImplementation(versionCatalog.findBundle("mockito").get())
    testImplementation(versionCatalog.findDependency("assertj").get())

    // Don't add a test framework by default since we use junit4, junit5, and testng depending on project
}

// TODO: Can we model this using https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures
val testArtifacts by configurations.creating
val testJar = tasks.register<Jar>("testJar") {
    archiveBaseName.set("${project.name}-test")
    from(sourceSets.test.get().output)
}

// Silly but allows higher throughput of the build because we can start compiling / testing other modules while the tests run
// This works because the sourceSet 'integrationTest' extends 'test', so it won't compile until after 'test' is compiled, but the
// task graph goes 'compileTest*' -> 'test' -> 'compileIntegrationTest*' -> 'testJar'.
// By flipping the order of the graph slightly, we can unblock downstream consumers of the testJar to start running tasks while this project
// can be executing the 'test' task.
tasks.test {
    mustRunAfter(testJar)
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
        junitXml.required.set(true)
        html.required.set(true)
    }

    testlogger {
        showFullStackTraces = true
        showStandardStreams = true
        showPassedStandardStreams = false
        showSkippedStandardStreams = true
        showFailedStandardStreams = true
    }

    configure<JacocoTaskExtension> {
        // don't instrument sdk, icons, etc.
        includes = listOf("software.aws.toolkits.*")
    }

    // https://github.com/JetBrains/intellij-community/blob/8c9301405d1ac014f121eda8b5c371c41f6f2b95/plugins/devkit/devkit-core/src/run/OpenedPackages.txt
    listOf(
        "java.base/java.lang=ALL-UNNAMED",
        "java.base/java.lang.reflect=ALL-UNNAMED",
        "java.base/java.text=ALL-UNNAMED",
        "java.base/java.time=ALL-UNNAMED",
        "java.base/java.util=ALL-UNNAMED",
        "java.base/java.util.concurrent=ALL-UNNAMED",
        "java.base/java.io=ALL-UNNAMED",
        "java.base/java.net=ALL-UNNAMED",
        "java.base/java.nio.charset=ALL-UNNAMED",
        "java.base/jdk.internal.vm=ALL-UNNAMED",
        "java.base/sun.nio.ch=ALL-UNNAMED",
        "java.desktop/java.awt=ALL-UNNAMED",
        "java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
        "java.desktop/java.awt.event=ALL-UNNAMED",
        "java.desktop/java.awt.image=ALL-UNNAMED",
        "java.desktop/java.awt.peer=ALL-UNNAMED",
        "java.desktop/javax.swing=ALL-UNNAMED",
        "java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        "java.desktop/javax.swing.text.html=ALL-UNNAMED",
        "java.desktop/sun.awt=ALL-UNNAMED",
        "java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
        "java.desktop/sun.awt.image=ALL-UNNAMED",
        "java.desktop/sun.awt.windows=ALL-UNNAMED",
        "java.desktop/sun.awt.X11=ALL-UNNAMED",
        "java.desktop/sun.font=ALL-UNNAMED",
        "java.desktop/sun.java2d=ALL-UNNAMED",
        "java.desktop/sun.lwawt=ALL-UNNAMED",
        "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        "java.desktop/sun.swing=ALL-UNNAMED",
        "java.desktop/com.apple.eawt=ALL-UNNAMED",
        "java.desktop/com.apple.eawt.event=ALL-UNNAMED",
        "java.desktop/com.apple.laf=ALL-UNNAMED",
        "jdk.attach/sun.tools.attach=ALL-UNNAMED",
        "jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
        "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
    ).forEach {
        jvmArgs("--add-opens=$it")
    }
}

// Jacoco configs taken from official Gradle docs: https://docs.gradle.org/current/userguide/structuring_software_products.html

// Do not generate reports for individual projects, see toolkit-jacoco-report plugin
tasks.jacocoTestReport.configure {
    enabled = false
}

// Share sources folder with other projects for aggregated JaCoCo reports
configurations.create("transitiveSourcesElements") {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = true
    extendsFrom(configurations.implementation.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("source-folders"))
    }
    sourceSets.main.get().java.srcDirs.forEach {
        outgoing.artifact(it)
    }
}

// Share the coverage data to be aggregated for the whole product
configurations.create("coverageDataElements") {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = true
    extendsFrom(configurations.implementation.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("jacoco-coverage-data"))
    }
    tasks.withType<Test> {
        outgoing.artifact(extensions.getByType<JacocoTaskExtension>().destinationFile!!)
    }
}
