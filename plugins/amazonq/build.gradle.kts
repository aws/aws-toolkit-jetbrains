// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import software.aws.toolkits.gradle.changelog.tasks.GeneratePluginChangeLog
import java.net.URI
import java.net.URL

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

val changelog = tasks.register<GeneratePluginChangeLog>("pluginChangeLog") {
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

abstract class DownloadFlareArtifactsTask : DefaultTask() {
    @get:InputFile
    abstract val manifestFile: RegularFileProperty
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @TaskAction
    fun download() {
        val manifest = jacksonObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue(manifestFile.get().asFile.readText(), FlareManifest::class.java)
        
        val latest = manifest.versions.first()
        val darwin = latest.targets.first { it.platform == "darwin" && it.arch == "arm64" }
        val urls = darwin.contents.map { it.url } + latest.thirdPartyLicenses
        
        val destDir = outputDir.get().asFile
        destDir.mkdirs()
        
        urls.forEach { url ->
            val uri = URI(url)
            val fileName = uri.path.substringAfterLast('/')
            val destFile = destDir.resolve(fileName)
            if (!destFile.exists()) {
                logger.info("Downloading $url to $destFile")
                uri.toURL().openStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

val downloadFlareArtifacts by tasks.registering(DownloadFlareArtifactsTask::class) {
    dependsOn(downloadFlareManifest)
    manifestFile.set(layout.buildDirectory.file("flare/manifest.json"))
    outputDir.set(layout.buildDirectory.dir("flare/artifacts"))
}

abstract class ExtractFlareTask : DefaultTask() {
    @get:InputFiles
    abstract val zipFiles: ConfigurableFileCollection
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @TaskAction
    fun extract() {
        val destDir = outputDir.get().asFile
        destDir.deleteRecursively()
        destDir.mkdirs()
        
        zipFiles.filter { it.name.endsWith(".zip") }.forEach { zipFile ->
            logger.info("Extracting flare from ${zipFile}")
            project.copy {
                from(project.zipTree(zipFile)) {
                    include("*.js")
                    include("*.txt")
                }
                into(destDir)
            }
        }
    }
}

val prepareBundledFlare by tasks.registering(ExtractFlareTask::class) {
    dependsOn(downloadFlareArtifacts)
    zipFiles.from(downloadFlareArtifacts.map { it.outputDir.asFileTree })
    outputDir.set(layout.buildDirectory.dir("tmp/extractFlare"))
}

tasks.withType<PrepareSandboxTask>().configureEach {
    from(file("contrib/QCT-Maven-6-16.jar")) {
        into(intellijPlatform.projectName.map { "$it/lib" })
    }
    from(prepareBundledFlare) {
        into(intellijPlatform.projectName.map { "$it/flare" })
    }
}
