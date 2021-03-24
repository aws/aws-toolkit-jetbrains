// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.intellij

plugins {
    id("org.jetbrains.intellij")
}

val ideProfile = IdeVersions.ideProfile(project)

val publishToken: String by project
val publishChannel: String by project

intellij {
    version = ideProfile.community.sdkVersion
    pluginName = "aws-toolkit-jetbrains"
    updateSinceUntilBuild = false
}

tasks.prepareSandbox {
    project.findProject(":jetbrains-rider")?.let {
        from(tasks.getByPath(":jetbrains-rider:prepareSandbox"))
    }
}

tasks.publishPlugin {
    token(publishToken)
    channels(publishChannel.split(",").map { it.trim() })
}

tasks.check {
    dependsOn(tasks.named("verifyPlugin"))
}

tasks.test {
    enabled = false
}

dependencies {
    implementation(project(":jetbrains-core"))
    implementation(project(":jetbrains-ultimate"))
    project.findProject(":jetbrains-rider")?.let {
        implementation(it)
    }
}
