// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import groovy.lang.Closure
import org.jetbrains.intellij.IntelliJPluginExtension

val ideSdkVersion: Closure<String> by ext
val idePlugins: Closure<Array<String>> by ext

apply(plugin = "org.jetbrains.intellij")

dependencies {
    api(project(":jetbrains-core"))
    testImplementation(project(path = ":jetbrains-core", configuration = "testArtifacts"))
    testImplementation(project(path = ":core", configuration = "testArtifacts"))
    integrationTestImplementation(project(path = ":jetbrains-core", configuration = "testArtifacts"))
}

configure<IntelliJPluginExtension> {
    val parentIntellijTask = project(":jetbrains-core").task("intellij") as IntelliJPluginExtension
    version = ideSdkVersion("IU")
    plugins = idePlugins("IU")
    pluginName = parentIntellijTask.pluginName
    updateSinceUntilBuild = parentIntellijTask.updateSinceUntilBuild
    downloadSources = parentIntellijTask.downloadSources
}

tasks.withType(Test::class.java) {
    systemProperty("log.dir", "${(project.task("intellij") as IntelliJPluginExtension).sandboxDirectory}-test/logs")
}

tasks.withType(Jar::class.java) {
    archiveBaseName.set("aws-intellij-toolkit-ultimate")
}
