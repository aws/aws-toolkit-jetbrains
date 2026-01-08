// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IC)
}

dependencies {
    intellijPlatform {
        localPlugin(project(":plugin-core"))
        // Required for collaboration auth credentials in 2025.3+
        val version  = IdeVersions.ideProfile(project).ultimate.sdkVersion
        if (version.startsWith( "2025.3") ){
            bundledModule("intellij.platform.collaborationTools.auth.base")
            bundledModule("intellij.platform.collaborationTools.auth")
        }
    }

    implementation(project(":plugin-amazonq:shared:jetbrains-community"))
    // hack because transform has a chat entrypoint
    implementation(project(":plugin-amazonq:chat:jetbrains-community"))
    // hack because everything references codewhisperer
    implementation(project(":plugin-amazonq:codewhisperer:jetbrains-community"))

    compileOnly(project(":plugin-core:jetbrains-community"))

    testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))
}

// hack because our test structure currently doesn't make complete sense
tasks.prepareTestSandbox {
    val pluginXmlJar = project(":plugin-amazonq").tasks.jar

    dependsOn(pluginXmlJar)
    from(pluginXmlJar) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
}
