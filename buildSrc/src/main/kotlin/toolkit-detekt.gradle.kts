// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import software.aws.toolkits.gradle.jvmTarget
import kotlin.jvm.java

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("toolkit-testing")
}

// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    detektPlugins(versionCatalog.findLibrary("detekt-formattingRules").get())
    detektPlugins(project(":detekt-rules"))
}

private val detektFiles = fileTree(projectDir).matching {
    include("**/*.kt", "**/*.kts")
    exclude("**/build")
}

detekt {
    val rulesProject = project(":detekt-rules").projectDir
    source.setFrom(detektFiles)
    buildUponDefaultConfig = true
    parallel = true
    allRules = false
    config.setFrom("$rulesProject/detekt.yml")
    autoCorrect = true
}

val javaVersion = project.jvmTarget().get()

tasks.withType<Detekt>().configureEach {
    doFirst {
        detektFiles.forEach { println(it) }
    }

    jvmTarget = javaVersion.majorVersion
    dependsOn(":detekt-rules:assemble")

    reports {
        html.required.set(true) // Human readable report
        xml.required.set(true) // Checkstyle like format for CI tool integrations
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = javaVersion.majorVersion
    dependsOn(":detekt-rules:assemble")

    // weird issue where the baseline tasks can't find the source code
    source.plus(detektFiles)
}

tasks.create("aaaa") {
    doLast {
        project.extensions.getByType(KotlinJvmProjectExtension::class.java).target.compilations.all { compilation ->
            val ss = compilation.kotlinSourceSets
                .map { it.kotlin.sourceDirectories }
                .fold(project.files() as FileCollection) { collection, next -> collection.plus(next) }
            ss
                .forEach { println(it) }

            println("1 / ======")
            println(ss.files)

            println("pp / ======")
            ss.forEach { t ->
                println("pp / $t / ======")
                exec {
                    commandLine("ls", "-laR", t.absolutePath).isIgnoreExitValue = true
                }
            }
            true
        }

        println("source / ======")
        tasks.named<Detekt>("detektMain").get().source.forEach { println(it) }
        println("files / ======")
        println(tasks.named<Detekt>("detektMain").get().source.files)

    }
}
