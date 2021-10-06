// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.gradle.testing.jacoco.plugins.JacocoTaskExtension.Output
import org.jetbrains.intellij.tasks.DownloadRobotServerPluginTask
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask
import software.aws.toolkits.gradle.ciOnly
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension
import software.aws.toolkits.gradle.isCi

val toolkitIntelliJ = project.extensions.create<ToolkitIntelliJExtension>("intellijToolkit")

val ideProfile = IdeVersions.ideProfile(project)
val toolkitVersion: String by project
val remoteRobotPort: String by project
val remoteRobotVersion: String by project

// please check changelog generation logic if this format is changed
version = "$toolkitVersion-${ideProfile.shortName}"

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("org.jetbrains.intellij")
}

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

    plugins.withType<ToolkitIntegrationTestingPlugin> {
        maybeCreate("integrationTest").apply {
            java.srcDirs(findFolders(project, "it", ideProfile))
            resources.srcDirs(findFolders(project, "it-resources", ideProfile))
        }
    }
}

configurations {
    runtimeClasspath {
        // Exclude dependencies that ship with iDE
        exclude(group = "org.slf4j")
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")

        // Exclude dependencies we don't use to make plugin smaller
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
}

tasks.processResources {
    // needed because both rider and ultimate include plugin-datagrip.xml which we are fine with
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Run after the project has been evaluated so that the extension (intellijToolkit) has been configured
intellij {
    pluginName.set("aws-toolkit-jetbrains")

    localPath.set(toolkitIntelliJ.localPath())
    version.set(toolkitIntelliJ.version())

    plugins.set(toolkitIntelliJ.productProfile().map { it.plugins.toMutableList() })

    downloadSources.set(toolkitIntelliJ.ideFlavor.map { it == IdeFlavor.IC && !project.isCi() })
    instrumentCode.set(toolkitIntelliJ.ideFlavor.map { it != IdeFlavor.RD })
}

tasks.jar {
    archiveBaseName.set(toolkitIntelliJ.ideFlavor.map { "aws-toolkit-jetbrains-$it" })
}

tasks.patchPluginXml {
    sinceBuild.set(toolkitIntelliJ.ideProfile().map { it.sinceVersion })
    untilBuild.set(toolkitIntelliJ.ideProfile().map { it.untilVersion })
}

// Disable building the settings search cache since it 1. fails the build, 2. gets run on the final packaged plugin
tasks.buildSearchableOptions {
    enabled = false
}

tasks.withType<Test>().all {
    systemProperty("log.dir", intellij.sandboxDir.map { "$it-test/logs" }.get())
    systemProperty("testDataPath", project.rootDir.resolve("testdata").absolutePath)
}

tasks.withType<JavaExec> {
    systemProperty("aws.toolkits.enableTelemetry", false)
}

tasks.runIde {
    systemProperty("aws.toolkit.developerMode", true)

    val alternativeIde = providers.environmentVariable("ALTERNATIVE_IDE").forUseAtConfigurationTime()
    if (alternativeIde.isPresent) {
        // remove the trailing slash if there is one or else it will not work
        val value = alternativeIde.get()
        val path = File(value.trimEnd('/'))
        if (path.exists()) {
            ideDir.set(path)
        } else {
            throw GradleException("ALTERNATIVE_IDE path not found $value")
        }
    }
}

tasks.withType<DownloadRobotServerPluginTask> {
    version.set(remoteRobotVersion)
}

// Enable coverage for the UI test target IDE
ciOnly {
    extensions.getByType<JacocoPluginExtension>().applyTo(tasks.withType<RunIdeForUiTestTask>())
}
tasks.withType<RunIdeForUiTestTask>().all {
    systemProperty("robot-server.port", remoteRobotPort)
    systemProperty("ide.mac.file.chooser.native", "false")
    systemProperty("jb.consents.confirmation.enabled", "false")
    // This does some magic in EndUserAgreement.java to make it not show the privacy policy
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("ide.show.tips.on.startup.default.value", false)

    systemProperty("aws.telemetry.skip_prompt", "true")
    systemProperty("aws.suppress_deprecation_prompt", true)

    // These are experiments to enable for UI tests
    systemProperty("aws.feature.connectedLocalTerminal", true)
    systemProperty("aws.feature.dynamoDb", true)
    ciOnly {
        systemProperty("aws.sharedCredentialsFile", "/tmp/.aws/credentials")
    }

    debugOptions {
        enabled.set(true)
        suspend.set(false)
    }

    ciOnly {
        configure<JacocoTaskExtension> {
            includes = listOf("software.aws.toolkits.*")
            output = Output.TCP_CLIENT // Dump to our jacoco server instead of to a file
        }
    }
}
