// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.telemetry.generator.gradle.GenerateTelemetry

plugins {
    id("java-library")
    id("toolkit-testing")
    id("toolkit-intellij-subplugin")
}

buildscript {
    dependencies {
        classpath(libs.telemetryGenerator)
    }
}

private val generatedSrcDir = project.layout.buildDirectory.dir("generated-src")
sourceSets {
    main {
        java.srcDir(generatedSrcDir)
    }
    test {
        java.setSrcDirs(emptySet<String>())
    }
}

idea {
    module {
        generatedSourceDirs = generatedSourceDirs.toMutableSet() + generatedSrcDir.get().asFile
    }
}

val generateTelemetry = tasks.register<GenerateTelemetry>("generateTelemetry") {
    inputFiles.setFrom(file("${project.projectDir}/resources/telemetryOverride.json"))
    outputDirectory.set(generatedSrcDir)

    doFirst {
        outputDirectory.get().asFile.deleteRecursively()
    }
}

val replaceInGeneratedSources = tasks.register<DefaultTask>("replaceInGeneratedSources") {
    dependsOn(generateTelemetry)

    doLast {
        // Define your string replacements as pairs (old -> new)
        val replacements = mapOf(
            "software.aws.toolkits.jetbrains.services.telemetry" to "software.amazon.q.jetbrains.services.telemetry",
            "software.aws.toolkits.core" to "software.amazon.q.core"
        )

        // Walk through all generated source files
        generatedSrcDir.get().asFile.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                var content = file.readText()
                var modified = false

                // Apply each replacement
                replacements.forEach { (oldString, newString) ->
                    val newContent = content.replace(oldString, newString)
                    if (newContent != content) {
                        modified = true
                        content = newContent
                    }
                }

                // Write back only if changes were made
                if (modified) {
                    file.writeText(content)
                    println("Replaced strings in: ${file.name}")
                }
            }
    }
}

tasks.compileKotlin {
    dependsOn(replaceInGeneratedSources)
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

// expose intellij test framework to fixture consumers
configurations.testFixturesCompileOnlyApi {
    extendsFrom(
        configurations.intellijPlatformTestDependencies.get()
    )
}

// intellij java-test-framework pollutes test classpath with extracted java plugins
configurations.testFixturesApi {
    exclude("com.jetbrains.intellij.java", "java")
    exclude("com.jetbrains.intellij.java", "java-impl")
}

dependencies {
    compileOnlyApi(project(":plugin-core-q:core-q"))
    compileOnlyApi(libs.aws.apacheClient)
    compileOnlyApi(libs.aws.nettyClient)

    api(libs.aws.iam)

    testFixturesApi(project(path = ":plugin-core-q:core-q", configuration = "testArtifacts"))
    testFixturesApi(project(":plugin-core-q:resources"))
    testFixturesApi(libs.wiremock) {
        // conflicts with transitive inclusion from docker plugin
        exclude(group = "org.apache.httpcomponents.client5")
        // provided by IDE
        exclude(group = "commons-io")
    }

    testImplementation(project(":plugin-core-q:core-q"))
    testRuntimeOnly(project(":plugin-core-q:sdk-codegen"))
}

// fix implicit dependency on generated source
tasks.withType<Detekt>().configureEach {
    dependsOn(generateTelemetry)
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    dependsOn(generateTelemetry)
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-core-q").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}

tasks.test {
    enabled = false
}
