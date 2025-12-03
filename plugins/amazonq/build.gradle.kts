// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import software.aws.toolkits.gradle.changelog.tasks.GenerateAmazonQPluginChangeLog

plugins {
    id("toolkit-publishing-conventions")
    id("toolkit-publish-root-conventions")
    id("toolkit-jvm-conventions")
    id("toolkit-testing")
    id("de.undercouch.download")
}

buildscript {
    dependencies {
        classpath(libs.bundles.jackson)
    }
}

val changelog = tasks.register<GenerateAmazonQPluginChangeLog>("pluginChangeLog") {
    includeUnreleased.set(true)
    changeLogFile.value(layout.buildDirectory.file("changelog/change-notes.xml"))
}

tasks.jar {
    dependsOn(changelog)
    from(changelog) {
        into("META-INF")
    }
}

dependencies {
    intellijPlatform {
        localPlugin(project(":plugin-core"))
    }

    implementation(project(":plugin-amazonq:chat"))
    implementation(project(":plugin-amazonq:codetransform"))
    implementation(project(":plugin-amazonq:codewhisperer"))
    implementation(project(":plugin-amazonq:mynah-ui"))
    implementation(project(":plugin-amazonq:shared"))
    implementation(libs.bundles.jackson)
    implementation(libs.lsp4j)

    testImplementation(project(":plugin-core"))
}

tasks.check {
    val serviceSubdirs = project(":plugin-amazonq").subprojects
    serviceSubdirs.forEach { serviceSubDir ->
        val subDirs = serviceSubDir.subprojects
        subDirs.forEach { insideService->
            dependsOn(":plugin-amazonq:${serviceSubDir.name}:${insideService.name}:check")
        }
    }
}

val downloadFlareManifest by tasks.registering(Download::class) {
    src("https://aws-toolkit-language-servers.amazonaws.com/qAgenticChatServer/0/manifest.json")
    dest(layout.buildDirectory.file("flare/manifest.json"))
    onlyIfModified(true)
    useETag(true)
}

data class FlareManifest(
    val versions: List<FlareVersion>,
)

data class FlareVersion(
    val serverVersion: String,
    val thirdPartyLicenses: String,
    val targets: List<FlareTarget>,
)

data class FlareTarget(
    val platform: String,
    val arch: String,
    val contents: List<FlareContent>
)

data class FlareContent(
    val url: String,
)

val downloadFlareArtifacts by tasks.registering(Download::class) {
    dependsOn(downloadFlareManifest)
    inputs.files(downloadFlareManifest)

    val manifestFile = downloadFlareManifest.map { it.outputFiles.first() }
    val manifest = manifestFile.map { jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(it.readText(), FlareManifest::class.java) }

    // use darwin-aarch64 because its the smallest and we're going to throw away everything platform specific
    val latest = manifest.map { it.versions.first() }
    val latestVersion = latest.map { it.serverVersion }
    val licensesUrl = latest.map { it.thirdPartyLicenses }
    val darwin = latest.map { it.targets.first { target -> target.platform == "darwin" && target.arch == "arm64" } }
    val contentUrls = darwin.map { it.contents.map { content -> content.url } }

    val destination = layout.buildDirectory.dir(latestVersion.map { "flare/$it" })
    outputs.dir(destination)

    src(contentUrls.zip(licensesUrl) { left, right -> left + right})
    dest(destination)
    onlyIfModified(true)
    useETag(true)
}

val prepareBundledFlare by tasks.registering(Copy::class) {
    dependsOn(downloadFlareArtifacts)
    inputs.files(downloadFlareArtifacts)

    val dest = layout.buildDirectory.dir("tmp/extractFlare")
    into(dest)
    from(downloadFlareArtifacts.map { it.outputFiles.filterNot { file -> file.name.endsWith(".zip") } })

    doLast {
        copy {
            into(dest)
            includeEmptyDirs = false
            downloadFlareArtifacts.get().outputFiles.filter { it.name.endsWith(".zip") }.forEach {
                dest.get().file(it.parentFile.name).asFile.createNewFile()
                from(zipTree(it)) {
                    include("*.js")
                    include("*.txt")
                }
            }
        }
    }
}

tasks.withType<PrepareSandboxTask>().configureEach {
    from(file("contrib/QCT-Maven-1-0-156-0.jar")) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
    from(prepareBundledFlare) {
        into(intellijPlatform.projectName.map { "$it/flare" })
    }
}
