// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
import software.aws.toolkits.gradle.intellij.toolkitIntelliJ

val ideProfile = IdeVersions.ideProfile(project)

plugins {
    id("toolkit-intellij-plugin")
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
}

// TODO: https://github.com/gradle/gradle/issues/15383
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

// Add our source sets per IDE profile version (i.e. src-211)
sourceSets {
    main {
        java.srcDirs(findFolders(project, "src", ideProfile))
        resources.srcDirs(findFolders(project, "resources", ideProfile))
    }
    test {
        java.srcDirs(findFolders(project, "tst", ideProfile))
        resources.srcDirs(findFolders(project, "tst-resources", ideProfile))
    }
}

configurations {
    runtimeClasspath {
        // IDE provides Kotlin
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }

    configureEach {
        // IDE provides netty
        exclude("io.netty")

        if (name.startsWith("detekt")) {
            return@configureEach
        }

        // Exclude dependencies that ship with iDE
        exclude(group = "org.slf4j")
        if (!name.startsWith("kotlinCompiler") && !name.startsWith("generateModels") && !name.startsWith("rdGen")) {
            // we want kotlinx-coroutines-debug and kotlinx-coroutines-test
            exclude(group = "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm")
            exclude(group = "org.jetbrains.kotlinx", "kotlinx-coroutines-core")
        }

        val configName = name
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
                useVersion(versionCatalog.findVersion("kotlinCoroutines").get().toString())
                because("resolve kotlinx-coroutines version conflicts in favor of local version catalog")
            }

            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin")) {
                // Only stdlib-like artifacts on runtime/test configurations should use the IDE-bundled version,
                // and only when the IDE bundles a NEWER version than the KGP compiler.
                // Compiler plugins (kotlin-scripting-*, kotlin-build-tools-*, etc.) must stay at KGP version.
                val kgpVersion = versionCatalog.findVersion("kotlin").get().toString()
                val ideStdlibVersion = versionCatalog.findVersion("kotlinStdlib").get().toString()
                val isRuntimeOrTest = configName.contains("ompileClasspath") ||
                    configName.contains("untimeClasspath") ||
                    configName.contains("estFixtures")
                val isStdlibLike = requested.name.startsWith("kotlin-stdlib") ||
                    requested.name == "kotlin-reflect" ||
                    requested.name.startsWith("kotlin-test")
                val version = if (isRuntimeOrTest && isStdlibLike && ideStdlibVersion > kgpVersion) {
                    ideStdlibVersion
                } else {
                    kgpVersion
                }
                useVersion(version)
                because("resolve kotlin version conflicts: use IDE-bundled version when newer, otherwise KGP version")
            }

            // https://nvd.nist.gov/vuln/detail/cve-2022-25647
            if (requested.group == "com.google.code.gson" && requested.name == "gson") {
                useVersion("2.11.0")
                because("CVE-2022-25647 requires Gson >= 2.8.9")
            }
        }
    }
}

tasks.processResources {
    // needed because both rider and ultimate include plugin-datagrip.xml which we are fine with
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.processTestResources {
    // TODO how can we remove this
    duplicatesStrategy = DuplicatesStrategy.WARN
}

// Run after the project has been evaluated so that the extension (intellijToolkit) has been configured
intellijPlatform {
    // find the name of first subproject depth, or root if not applied to a subproject hierarchy
    projectName.convention(generateSequence(project) { it.parent }.first { it.depth <= 1 }.name)
    instrumentCode = true
}

dependencies {
    intellijPlatform {
        val version = toolkitIntelliJ.version()

        // annoying resolution issue that we don't want to bother fixing
        if (!project.name.contains("jetbrains-gateway")) {
            val type = toolkitIntelliJ.ideFlavor.map { flavor ->
                // Starting with 2025.3, IntelliJ IDEA is unified (no separate Community edition)
                if ((version.get().startsWith("2025.3") || version.get().startsWith("2026.")) && flavor == IdeFlavor.IC) {
                    IntelliJPlatformType.IntellijIdeaUltimate
                } else {
                    IntelliJPlatformType.fromCode(flavor.toString())
                }
            }

            create(type, version, useInstaller = false)
        } else {
            create(IntelliJPlatformType.Gateway, version)

            // Gateway 2026.1 product-info.json has layout entries without "classPath".
            // intellij-plugin-structure crashes on these (ProductModuleV2.classPath is non-null).
            // Bug exists on master: https://github.com/JetBrains/intellij-plugin-verifier
            // Force-resolve IDE, patch product-info.json, then let bundledPlugins proceed.
            afterEvaluate {
                configurations.findByName("intellijPlatformDependency")?.resolve()
                software.aws.toolkits.gradle.intellij.ProductInfoPatcher.patchGatewayProductInfo(
                    gradle.gradleUserHomeDir
                )
            }
        }

        bundledPlugins(toolkitIntelliJ.productProfile().map { it.bundledPlugins })
        plugins(toolkitIntelliJ.productProfile().map { it.marketplacePlugins })

        // OAuth modules split in 2025.3+ - must be explicitly bundled
        val versionStr = version.get()
        if (versionStr.contains("253") || versionStr.startsWith("2025.3") ||
            versionStr.startsWith("2026.")) {
            bundledModule("intellij.platform.collaborationTools")
            bundledModule("intellij.platform.collaborationTools.auth.base")
            bundledModule("intellij.platform.collaborationTools.auth")
        }

        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

// https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1844
tasks.withType<PrepareSandboxTask>().configureEach {
    disabledPlugins.addAll(
        "com.intellij.swagger",
        "org.jetbrains.plugins.kotlin.jupyter",
    )
}

tasks.jar {
    // :plugin-toolkit:jetbrains-community results in: --plugin-toolkit-jetbrains-community-IC-<version>.jar
    archiveBaseName.set(toolkitIntelliJ.ideFlavor.map { "${project.buildTreePath.replace(':', '-')}-$it" })
}

tasks.withType<Test>().configureEach {
    // conflict with Docker logging impl; so bypass service loader
    systemProperty("slf4j.provider", "org.slf4j.jul.JULServiceProvider")

    systemProperty("log.dir", intellijPlatform.sandboxContainer.map { "$it-test/logs" }.get())
    systemProperty("testDataPath", project.rootDir.resolve("testdata").absolutePath)
    val jetbrainsCoreTestResources = project(":plugin-toolkit:jetbrains-core").projectDir.resolve("tst-resources")
    systemProperty("idea.log.config.properties.file", jetbrainsCoreTestResources.resolve("toolkit-test-log.properties"))
    systemProperty("org.gradle.project.ideProfileName", ideProfile.name)
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("aws.toolkits.enableTelemetry", false)
}
