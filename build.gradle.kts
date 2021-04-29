// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog
import io.gitlab.arturbosch.detekt.Detekt

val ideProfile = IdeVersions.ideProfile(project)
val toolkitVersion: String by project
val detektVersion: String by project

plugins {
    id("base")
    id("toolkit-changelog")
    id("toolkit-jacoco-report")
    id("io.gitlab.arturbosch.detekt").version("1.16.0")
}

allprojects {
    repositories {
        mavenLocal()
        System.getenv("CODEARTIFACT_URL")?.let {
            println("Using CodeArtifact proxy: $it")
            maven {
                url = uri(it)
                credentials {
                    username = "aws"
                    password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
                }
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
        detektPlugins(project(":detekt-rules"))
    }

    detekt {
        input.from("$projectDir")
        buildUponDefaultConfig = false
        parallel = true
        allRules = false
        config = files("$rootDir/detekt-rules/detekt.yml")

        reports {
            html.enabled = true // observe findings in your browser with structure and code snippets
            xml.enabled = true // checkstyle like format mainly for integrations like Jenkins
            sarif.enabled = true // standardized SARIF format to support integrations with Github Code Scanning
        }
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "1.8"
        dependsOn(":detekt-rules:assemble")
    }
}

tasks.register<GenerateGithubChangeLog>("generateChangeLog") {
    changeLogFile.set(project.file("CHANGELOG.md"))
}

tasks.createRelease.configure {
    releaseVersion.set(providers.gradleProperty("toolkitVersion"))
}

dependencies {
    aggregateCoverage(project(":intellij"))
    aggregateCoverage(project(":ui-tests"))
}

tasks.register("runIde") {
    doFirst {
        throw GradleException("Use project specific runIde command, i.e. :jetbrains-core:runIde, :intellij:runIde")
    }
}

tasks.check {
    dependsOn(tasks.detekt)
}
