// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareTestTask
import software.aws.toolkits.gradle.intellij.IdeFlavor

plugins {
    id("toolkit-intellij-subplugin")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.IU)
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Metrics)

        localPlugin(project(":plugin-core"))
    }

    compileOnly(project(":plugin-amazonq:codewhisperer:jetbrains-community"))
    compileOnly(project(":plugin-amazonq:shared:jetbrains-ultimate"))

    compileOnly(project(":plugin-core:jetbrains-ultimate"))

    testImplementation(testFixtures(project(":plugin-amazonq:codewhisperer:jetbrains-community")))
    testImplementation(project(path = ":plugin-toolkit:jetbrains-ultimate", configuration = "testArtifacts"))
}

tasks.test {
    // custom test tasks can retrieve platformPath directly
    val platformPath = project.tasks.named<PrepareTestTask>(Tasks.PREPARE_TEST).get().platformPath
    systemProperty("idea.async.profiler.agent.path", platformPath.resolve("lib/async-profiler/libasyncProfiler.dylib").toString())

}
