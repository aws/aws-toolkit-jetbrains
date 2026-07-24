// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
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
            val version = toolkitIntelliJ.version().get()
            create(IntelliJPlatformType.IntellijIdeaUltimate, version)

            // Opt-in (only when -PverifyUpcoming is set, e.g. the scheduled verify-upcoming CI job) so normal
            // PR/release builds are unaffected. Verifies the current build against the NEXT major's pre-release
            // builds to catch platform API breaks during the EAP/RC window.
            if (providers.gradleProperty("verifyUpcoming").isPresent) {
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
                    channels = listOf(ProductRelease.Channel.EAP, ProductRelease.Channel.RC)
                    sinceBuild = IdeVersions.upcomingBranchNumber()
                    untilBuild = "${IdeVersions.upcomingBranchNumber()}.*"
                }
            }
        }
    }
}

if (providers.gradleProperty("verifyUpcoming").isPresent) {
    tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrintProductsReleasesTask>(
        org.jetbrains.intellij.platform.gradle.Constants.Tasks.PRINT_PRODUCTS_RELEASES
    ).configure {
        types.set(listOf(IntelliJPlatformType.IntellijIdeaUltimate))
        channels.set(listOf(ProductRelease.Channel.EAP, ProductRelease.Channel.RC))
        sinceBuild.set(IdeVersions.upcomingBranchNumber())
        untilBuild.set("${IdeVersions.upcomingBranchNumber()}.*")
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
                ideFlavor.set(IdeFlavor.values().firstOrNull { it.name == runIdeVariant.orNull } ?: defaultFlavor)
            }

            val sdkVersion = toolkitIntelliJ.version().get()
            when (toolkitIntelliJ.ideFlavor.get()) {
                IdeFlavor.IU -> intellijIdeaUltimate(sdkVersion) { useInstaller.set(false) }
                IdeFlavor.RD -> rider(sdkVersion) { useInstaller.set(false) }
                else -> {
                    if (sdkVersion.startsWith("2025.3") || sdkVersion.startsWith("2026.")) {
                        intellijIdeaUltimate(sdkVersion) { useInstaller.set(false) }
                    } else {
                        intellijIdeaCommunity(sdkVersion) { useInstaller.set(false) }
                    }
                }
            }
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
