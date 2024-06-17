// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import software.aws.toolkits.gradle.intellij.IdeVersions
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

plugins {
    id("org.jetbrains.intellij.platform")
}

intellijPlatform {
    publishing {
        val publishToken: String by project
        val publishChannel: String by project

        token.set(publishToken)
        channels.set(publishChannel.split(",").map { it.trim() })
    }

    verifyPlugin {
        // need to tune this
        failureLevel.set(listOf(VerifyPluginTask.FailureLevel.INVALID_PLUGIN))

        ides {
            // recommended() appears to resolve latest EAP for a product?git
            ide(provider { IntelliJPlatformType.IntellijIdeaCommunity }, toolkitIntelliJ.version())
            ide(provider { IntelliJPlatformType.IntellijIdeaUltimate }, toolkitIntelliJ.version())
        }
    }
}

configurations {
    all {
        // IDE provides netty
        exclude("io.netty")
    }

    // Make sure we exclude stuff we either A) ships with IDE, B) we don't use to cut down on size
    runtimeClasspath {
        exclude(group = "org.slf4j")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
}

tasks.check {
    dependsOn(tasks.verifyPlugin)
}
