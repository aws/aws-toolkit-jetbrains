// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("org.jetbrains.intellij")
    id("toolkit-testing") // Needed so the coverage configurations are present
    id("toolkit-detekt")
}

val ideProfile = IdeVersions.ideProfile(project)

val toolkitVersion: String by project
val publishToken: String by project
val publishChannel: String by project

// please check changelog generation logic if this format is changed
version = "$toolkitVersion-${ideProfile.shortName}"

val resharperDlls = configurations.create("resharperDlls") {
    isCanBeConsumed = false
}

intellij {
    pluginName.set("aws-toolkit-jetbrains")

    version.set(ideProfile.community.version())
    localPath.set(ideProfile.community.localPath())

    updateSinceUntilBuild.set(false)
    instrumentCode.set(false)
}

tasks.prepareSandbox {
    from(resharperDlls) {
        into("aws-toolkit-jetbrains/dotnet")
    }
}

tasks.publishPlugin {
    token.set(publishToken)
    channels.set(publishChannel.split(",").map { it.trim() })
}

tasks.check {
    dependsOn(tasks.verifyPlugin)
}

// We have no source in this project, so skip test task
tasks.test {
    enabled = false
}

dependencies {
    implementation(project(":jetbrains-core"))
    implementation(project(":jetbrains-ultimate"))
    project.findProject(":jetbrains-rider")?.let {
        implementation(it)
        resharperDlls(project(":jetbrains-rider", configuration = "resharperDlls"))
    }
}

configurations {
    // Make sure we exclude stuff we either A) ships with IDE, B) we don't use to cut down on size
    runtimeClasspath {
        exclude(group = "org.slf4j")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
}
