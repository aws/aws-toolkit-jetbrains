// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog

plugins {
    id("base")
    id("toolkit-changelog")
    id("toolkit-jacoco-report")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.1"
}

val codeArtifactUrl: Provider<String> = providers.environmentVariable("CODEARTIFACT_URL").forUseAtConfigurationTime()
val codeArtifactToken: Provider<String> = providers.environmentVariable("CODEARTIFACT_AUTH_TOKEN").forUseAtConfigurationTime()

allprojects {
    repositories {
        if (codeArtifactUrl.isPresent && codeArtifactToken.isPresent) {
            println("Using CodeArtifact proxy: ${codeArtifactUrl.get()}")
            maven {
                url = uri(codeArtifactUrl.get())
                credentials {
                    username = "aws"
                    password = codeArtifactToken.get()
                }
            }
        }
        mavenCentral()
        gradlePluginPortal()
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

if (idea.project != null) { // may be null during script compilation
    idea {
        project {
            settings {
                taskTriggers {
                    afterSync(":sdk-codegen:generateSdks")
                    afterSync(":jetbrains-core:generateTelemetry")
                }
            }
        }
    }
}

fun org.gradle.plugins.ide.idea.model.IdeaProject.settings(configuration: ProjectSettings.() -> Unit) = (this as ExtensionAware).configure(configuration)
fun ProjectSettings.taskTriggers(action: TaskTriggersConfig.() -> Unit, ) = (this as ExtensionAware).extensions.configure("taskTriggers", action)

