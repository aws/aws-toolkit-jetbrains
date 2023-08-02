import org.jetbrains.kotlin.com.intellij.openapi.util.SystemInfoRt
import java.nio.file.Path
import kotlin.io.path.div

// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-detekt")
}

repositories {
    maven("https://packages.jetbrains.team/maven/p/tbx/gateway")
}

dependencies {
    api(project(":core"))
    api(project(":jetbrains-core"))
    api(project(":jetbrains-gateway"))
    implementation("com.jetbrains.toolbox.gateway:gateway-api:2.1.0.16315")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-jdk14:2.0.7")
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
    }

    from("resources") {
        include("extension.json")
        include("icon.svg")
    }

    into(targetDir)
}
