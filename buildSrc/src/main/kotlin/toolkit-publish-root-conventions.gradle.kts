// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

// publish-root should imply publishing-conventions, but we keep separate so that gateway always has the GW flavor
plugins {
    id("toolkit-intellij-plugin")
    id("org.jetbrains.intellij.platform")
}

tasks.withType<PatchPluginXmlTask>().configureEach {
    sinceBuild.set(toolkitIntelliJ.ideProfile().map { it.sinceVersion })
    untilBuild.set(toolkitIntelliJ.ideProfile().map { it.untilVersion })
}

intellijPlatform {
    instrumentCode = false

    pluginVerification {
        ides {
            // recommended() appears to resolve latest EAP for a product?
            // Starting with 2025.3, IntelliJ IDEA is unified (no separate Community edition)
            val version = toolkitIntelliJ.version().get()
            if (version.startsWith("2025.3")) {
                ide(provider { IntelliJPlatformType.IntellijIdeaUltimate }, toolkitIntelliJ.version())
            } else {
                ide(provider { IntelliJPlatformType.IntellijIdeaCommunity }, toolkitIntelliJ.version())
                ide(provider { IntelliJPlatformType.IntellijIdeaUltimate }, toolkitIntelliJ.version())
            }
        }
    }
}

dependencies {
    intellijPlatform {
        pluginVerifier()

        val alternativeIde = providers.environmentVariable("ALTERNATIVE_IDE")
        if (alternativeIde.isPresent) {
            // remove the trailing slash if there is one or else it will not work
            val value = alternativeIde.get()
            val path = File(value.trimEnd('/'))
            if (path.exists()) {
                local(path)
            } else {
                throw GradleException("ALTERNATIVE_IDE path not found $value")
            }
        } else {
            val runIdeVariant = providers.gradleProperty("runIdeVariant")

            // prefer versions declared in IdeVersions
            toolkitIntelliJ.apply {
                val defaultFlavor = if (version().get().startsWith("2025.3")) {
                    IdeFlavor.IU  // Use unified IntelliJ IDEA for 2025.3+
                } else {
                    IdeFlavor.IC  // Use Community for older versions
                }
                ideFlavor.convention(IdeFlavor.values().firstOrNull { it.name == runIdeVariant.orNull } ?: defaultFlavor)
            }
            val (type, version) = if (runIdeVariant.isPresent) {
                val type = toolkitIntelliJ.ideFlavor.map { IntelliJPlatformType.fromCode(it.toString()) }
                val version = toolkitIntelliJ.version()

                type to version
            } else {
                val defaultType = if (toolkitIntelliJ.version().get().startsWith("2025.3")) {
                    provider { IntelliJPlatformType.IntellijIdeaUltimate }
                } else {
                    provider { IntelliJPlatformType.IntellijIdeaCommunity }
                }
                defaultType to toolkitIntelliJ.version()
            }

            create(type, version, useInstaller = false)
            jetbrainsRuntime()
        }
    }
}

tasks.runIde {
    systemProperty("aws.toolkit.developerMode", true)
    systemProperty("ide.plugins.snapshot.on.unload.fail", true)
    systemProperty("memory.snapshots.path", project.rootDir)
    systemProperty("idea.auto.reload.plugins", false)

    val home = project.layout.buildDirectory.dir("USER_HOME").get()
    systemProperty("user.home", home)
    environment("HOME", home)
}

val runSplitIde by intellijPlatformTesting.runIde.registering {
    splitMode = true
    splitModeTarget = SplitModeAware.SplitModeTarget.BACKEND
}
