// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import software.aws.toolkits.gradle.intellij.IdeVersions
import software.aws.toolkits.gradle.intellij.ToolkitIntelliJExtension.IdeFlavor

buildscript {
    // Cannot be removed or else it will fail to compile
    @Suppress("RemoveRedundantQualifierName")
    val rdversion = software.aws.toolkits.gradle.intellij.IdeVersions.ideProfile(project).rider.rdGenVersion

    println("Using rd-gen: $rdversion")

    repositories {
        maven("https://www.myget.org/F/rd-snapshots/maven/")
    }

    dependencies {
        classpath("com.jetbrains.rd:rd-gen:$rdversion")
    }
}

val ideProfile = IdeVersions.ideProfile(project)

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-detekt")
    id("toolkit-intellij-subplugin")
    id("toolkit-testing")
    id("toolkit-integration-testing")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.RD)
}

sourceSets {
    main {
        java.srcDirs("$buildDir/generated-src")
    }
}

dependencies {
    implementation(project(":jetbrains-core"))
    testImplementation(project(path = ":jetbrains-core", configuration = "testArtifacts"))
}

/**
 * RESHARPER
 */

// Not published to gradle plugin portal, use old syntax
apply(plugin = "com.jetbrains.rdgen")

val resharperPluginPath = project.layout.projectDirectory.dir("ReSharper.AWS")
val resharperBuildPath = project.layout.buildDirectory.dir("dotnetBuild")

val resharperParts = listOf(
    "AWS.Daemon",
    "AWS.Localization",
    "AWS.Project",
    "AWS.Psi",
    "AWS.Settings"
)

val buildConfiguration = project.extra.properties["BuildConfiguration"] ?: "Debug" // TODO: Do we ever want to make a release build?

// Protocol
val protocolGroup = "protocol"

val csDaemonGeneratedOutput = resharperPluginPath.dir("src/AWS.Daemon/Protocol")
val csPsiGeneratedOutput = resharperPluginPath.dir("src/AWS.Psi/Protocol")
val csAwsSettingsGeneratedOutput = resharperPluginPath.dir("src/AWS.Settings/Protocol")
val csAwsProjectGeneratedOutput = resharperPluginPath.dir("src/AWS.Project/Protocol")

val riderGeneratedSources = project.layout.buildDirectory.dir("generated-src/software/aws/toolkits/jetbrains/protocol")

val modelDir = project.layout.projectDirectory.dir("protocol/model")
val rdgenDir = project.layout.buildDirectory.dir("rdgen").also {
    it.get().asFile.mkdirs()
}

configure<RdGenExtension> {
    verbose = true
    hashFolder = rdgenDir.get().asFile.absolutePath

    classpath({
        println("Calculating classpath for rdgen, intellij.ideaDependency is: ${intellij.ideaDependency}")
        File(intellij.ideaDependency.classes, "lib/rd").resolve("rider-model.jar").absolutePath
    })

    sources(projectDir.resolve("protocol/model"))
    packages = "model"
}

val generateModels = tasks.register<RdGenTask>("generateModels") {
    group = protocolGroup
    description = "Generates protocol models"

    inputs.dir(project.layout.projectDirectory.dir("protocol/model"))

    outputs.dir(riderGeneratedSources)
    outputs.dir(csDaemonGeneratedOutput)
    outputs.dir(csPsiGeneratedOutput)
    outputs.dir(csAwsSettingsGeneratedOutput)
    outputs.dir(csAwsProjectGeneratedOutput)

    systemProperty("ktDaemonGeneratedOutput", riderGeneratedSources.get().dir("DaemonProtocol").asFile.absolutePath)
    systemProperty("csDaemonGeneratedOutput", csDaemonGeneratedOutput.asFile.absolutePath)

    systemProperty("ktPsiGeneratedOutput", riderGeneratedSources.get().dir("PsiProtocol").asFile.absolutePath)
    systemProperty("csPsiGeneratedOutput", csPsiGeneratedOutput.asFile.absolutePath)

    systemProperty("ktAwsSettingsGeneratedOutput", riderGeneratedSources.get().dir("AwsSettingsProtocol").asFile.absolutePath)
    systemProperty("csAwsSettingsGeneratedOutput", csAwsSettingsGeneratedOutput.asFile.absolutePath)

    systemProperty("ktAwsProjectGeneratedOutput", riderGeneratedSources.get().dir("AwsProjectProtocol").asFile.absolutePath)
    systemProperty("csAwsProjectGeneratedOutput", csAwsProjectGeneratedOutput.asFile.absolutePath)
}

// Backend
val backendGroup = "backend"

val prepareBuildProps = tasks.register("prepareBuildProps") {
    val riderSdkVersionPropsPath = resharperPluginPath.file("RiderSdkPackageVersion.props")
    group = backendGroup

    inputs.property("riderNugetSdkVersion", ideProfile.rider.nugetVersion)
    outputs.file(riderSdkVersionPropsPath)

    doLast {
        val riderSdkVersion = ideProfile.rider.nugetVersion
        val configText = """<Project>
  <PropertyGroup>
    <RiderSDKVersion>[$riderSdkVersion]</RiderSDKVersion>
    <DefineConstants>PROFILE_${ideProfile.name.replace(".", "_")}</DefineConstants>
  </PropertyGroup>
</Project>
"""
        riderSdkVersionPropsPath.asFile.writeText(configText)
    }
}

val prepareNuGetConfig = tasks.register("prepareNuGetConfig") {
    group = backendGroup

    val nugetConfigPath = project.layout.projectDirectory.file("NuGet.Config")
    val nugetConfigPath211 = project.layout.projectDirectory.dir("testData").file("NuGet.config")

    inputs.property("rdVersion", ideProfile.rider.sdkVersion)
    outputs.files(nugetConfigPath, nugetConfigPath211)

    doLast {
        val nugetPath = getNugetPackagesPath()
        val configText = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="resharper-sdk" value="$nugetPath" />
  </packageSources>
</configuration>
"""
        nugetConfigPath.asFile.writeText(configText)
        nugetConfigPath211.asFile.writeText(configText)
    }
}

val buildReSharperPlugin = tasks.register("buildReSharperPlugin") {
    group = backendGroup
    description = "Builds the full ReSharper backend plugin solution"
    dependsOn(generateModels, prepareBuildProps, prepareNuGetConfig)

    inputs.files(generateModels)
    inputs.files(prepareBuildProps)
    inputs.files(prepareNuGetConfig)
    inputs.dir(resharperPluginPath)
    outputs.dir(resharperBuildPath)

    outputs.cacheIf { true }

    doLast {
        val arguments = listOf(
            "build",
            resharperPluginPath.file("ReSharper.AWS.sln").asFile.absolutePath
        )
        exec {
            executable = "dotnet"
            args = arguments
        }
    }
}

fun getNugetPackagesPath(): File {
    val sdkPath = intellij.ideaDependency.classes
    println("SDK path: $sdkPath")

    val riderSdk = File(sdkPath, "lib/DotNetSdkForRdPlugins")

    println("NuGet packages: $riderSdk")
    if (!riderSdk.isDirectory) throw IllegalStateException("$riderSdk does not exist or not a directory")

    return riderSdk
}

val resharperDlls = configurations.create("resharperDlls") {
    isCanBeResolved = false
}

val resharperDllsDir = tasks.register<Sync>("resharperDllsDir") {
    from(buildReSharperPlugin) {
        include("**/bin/**/$buildConfiguration/**/AWS*.dll")
        include("**/bin/**/$buildConfiguration/**/AWS*.pdb")
    }
    into("$buildDir/$name")

    includeEmptyDirs = false

    eachFile {
        path = name // Clear out the path to flatten it
    }

    // TODO how is this being called twice? Can we fix it?
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

artifacts {
    add(resharperDlls.name, resharperDllsDir)
}

tasks.clean {
    dependsOn("cleanGenerateModels", "cleanPrepareBuildProps", "cleanPrepareNuGetConfig", "cleanBuildReSharperPlugin")
}

// Tasks:
//
// `buildPlugin` depends on `prepareSandbox` task and then zips up the sandbox dir and puts the file in rider/build/distributions
// `runIde` depends on `prepareSandbox` task and then executes IJ inside the sandbox dir
// `prepareSandbox` depends on the standard Java `jar` and then copies everything into the sandbox dir

tasks.withType<PrepareSandboxTask>().all {
    dependsOn(resharperDllsDir)

    from(resharperDllsDir) {
        into("aws-toolkit-jetbrains/dotnet")
    }
}

tasks.compileKotlin {
    dependsOn(generateModels)
}

tasks.test {
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize = "1024m"
}

tasks.integrationTest {
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize = "1024m"
}
