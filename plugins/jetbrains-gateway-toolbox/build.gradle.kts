// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.nio.file.Path
import kotlin.io.path.div

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-detekt")
}

dependencies {
    api(project(":plugin-core:core"))
    api(project(":plugin-toolkit:jetbrains-core"))
    api(project(":plugin-toolkit:jetbrains-gateway"))
    compileOnly(libs.sshd.core)
    implementation("com.jetbrains.toolbox.gateway:gateway-api:2.5.0.32871")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-jdk14:2.0.7")
}
java {
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

val pluginId = "sample"
//
val assemblePlugin by tasks.registering(Jar::class) {
    archiveBaseName.set(pluginId)
    from(sourceSets.main.get().output)
}

val copyPlugin by tasks.creating(Copy::class.java) {
    dependsOn(assemblePlugin)

    val userHome = System.getProperty("user.home").let { Path.of(it) }
    val toolboxCachesDir = when {
        SystemInfoRt.isWindows -> System.getenv("LOCALAPPDATA")?.let { Path.of(it) } ?: (userHome / "AppData" / "Local")
        // currently this is the location that TBA uses on Linux
        SystemInfoRt.isLinux -> System.getenv("XDG_DATA_HOME")?.let { Path.of(it) } ?: (userHome / ".local" / "share")
        SystemInfoRt.isMac -> userHome / "Library" / "Caches"
        else -> error("Unknown os")
    } / "JetBrains" / "Toolbox"

    val pluginsDir = when {
        SystemInfoRt.isWindows -> toolboxCachesDir / "cache"
        SystemInfoRt.isLinux || SystemInfoRt.isMac -> toolboxCachesDir
        else -> error("Unknown os")
    } / "plugins"

    val targetDir = pluginsDir / pluginId
    val runtimeClasspath by configurations.getting

    from(assemblePlugin.get().outputs.files)

    val excludedJarPrefixes = listOf("gateway-api")
    val filteredClasspath = runtimeClasspath.filter { f ->
        !excludedJarPrefixes.any { p -> f.name.startsWith(p) }
    }

    from(filteredClasspath) {
        include("*.jar")
        exclude("kotlin*.jar")
    }

    from("resources") {
        include("extension.json")
        include("icon.svg")
    }

    into(targetDir)
}
