// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import software.aws.toolkits.gradle.ciOnly
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
import java.nio.file.Path

buildscript {
    // Cannot be removed or else it will fail to compile
    @Suppress("RemoveRedundantQualifierName")
    val rdversion = software.aws.toolkits.gradle.intellij.IdeVersions.ideProfile(project).rider.rdGenVersion

    println("Using rd-gen: $rdversion")

    repositories {
        maven("https://www.myget.org/F/rd-snapshots/maven/")
        mavenCentral()
    }

    dependencies {
        classpath("com.jetbrains.rd:rd-gen:$rdversion")
    }
}

val ideProfile = IdeVersions.ideProfile(project)

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-intellij-subplugin")
    id("toolkit-testing")
    id("toolkit-integration-testing")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.RD)
}

intellij {
    type.set("RD")
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

val resharperPluginPath = File(projectDir, "ReSharper.AWS")
val resharperBuildPath = File(project.buildDir, "dotnetBuild")

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

val csDaemonGeneratedOutput = File(resharperPluginPath, "src/AWS.Daemon/Protocol")
val csPsiGeneratedOutput = File(resharperPluginPath, "src/AWS.Psi/Protocol")
val csAwsSettingsGeneratedOutput = File(resharperPluginPath, "src/AWS.Settings/Protocol")
val csAwsProjectGeneratedOutput = File(resharperPluginPath, "src/AWS.Project/Protocol")

val riderGeneratedSources = File("$buildDir/generated-src/software/aws/toolkits/jetbrains/protocol")

val modelDir = File(projectDir, "protocol/model")
val rdgenDir = File("${project.buildDir}/rdgen/")

rdgenDir.mkdirs()

configure<RdGenExtension> {
    verbose = true
    hashFolder = rdgenDir.toString()

    classpath({
        val ijDependency = tasks.setupDependencies.flatMap { it.idea }.map { it.classes }.get()
        println("Calculating classpath for rdgen, intellij.ideaDependency is: $ijDependency")
        File(ijDependency, "lib/rd").resolve("rider-model.jar").absolutePath
    })

    sources(projectDir.resolve("protocol/model"))
    packages = "model"
}

// TODO: migrate to official rdgen gradle plugin https://www.jetbrains.com/help/resharper/sdk/Rider.html#plugin-project-jvm
val generateModels = tasks.register<RdGenTask>("generateModels") {
    group = protocolGroup
    description = "Generates protocol models"

    inputs.dir(file("protocol/model"))

    outputs.dir(riderGeneratedSources)
    outputs.dir(csDaemonGeneratedOutput)
    outputs.dir(csPsiGeneratedOutput)
    outputs.dir(csAwsSettingsGeneratedOutput)
    outputs.dir(csAwsProjectGeneratedOutput)

    systemProperty("ktDaemonGeneratedOutput", riderGeneratedSources.resolve("DaemonProtocol").absolutePath)
    systemProperty("csDaemonGeneratedOutput", csDaemonGeneratedOutput.absolutePath)

    systemProperty("ktPsiGeneratedOutput", riderGeneratedSources.resolve("PsiProtocol").absolutePath)
    systemProperty("csPsiGeneratedOutput", csPsiGeneratedOutput.absolutePath)

    systemProperty("ktAwsSettingsGeneratedOutput", riderGeneratedSources.resolve("AwsSettingsProtocol").absolutePath)
    systemProperty("csAwsSettingsGeneratedOutput", csAwsSettingsGeneratedOutput.absolutePath)

    systemProperty("ktAwsProjectGeneratedOutput", riderGeneratedSources.resolve("AwsProjectProtocol").absolutePath)
    systemProperty("csAwsProjectGeneratedOutput", csAwsProjectGeneratedOutput.absolutePath)
}

val cleanGenerateModels = tasks.register<Delete>("cleanGenerateModels") {
    group = protocolGroup
    description = "Clean up generated protocol models"

    delete(generateModels)
}

// Backend
val backendGroup = "backend"

val prepareBuildProps = tasks.register("prepareBuildProps") {
    val riderSdkVersionPropsPath = File(resharperPluginPath, "RiderSdkPackageVersion.props")
    group = backendGroup

    inputs.property("riderNugetSdkVersion", ideProfile.rider.nugetVersion)
    outputs.file(riderSdkVersionPropsPath)

    doLast {
        val netFrameworkTarget = ideProfile.rider.netFrameworkTarget
        val riderSdkVersion = ideProfile.rider.nugetVersion
        val configText = """<Project>
  <PropertyGroup>
    <NetFrameworkTarget>$netFrameworkTarget</NetFrameworkTarget>
    <RiderSDKVersion>[$riderSdkVersion]</RiderSDKVersion>
    <DefineConstants>PROFILE_${ideProfile.name.replace(".", "_")}</DefineConstants>
  </PropertyGroup>
</Project>
"""
        riderSdkVersionPropsPath.writeText(configText)
    }
}

val prepareNuGetConfig = tasks.register("prepareNuGetConfig") {
    group = backendGroup

    dependsOn(tasks.setupDependencies)

    val nugetConfigPath = File(projectDir, "NuGet.Config")
    // FIX_WHEN_MIN_IS_211 remove the projectDir one above
    val nugetConfigPath211 = Path.of(projectDir.absolutePath, "testData", "NuGet.config").toFile()

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
        nugetConfigPath.writeText(configText)
        nugetConfigPath211.writeText(configText)
    }
}

val buildReSharperPlugin = tasks.register("buildReSharperPlugin") {
    group = backendGroup
    description = "Builds the full ReSharper backend plugin solution"
    dependsOn(generateModels, prepareBuildProps, prepareNuGetConfig)

    inputs.dir(resharperPluginPath)
    outputs.dir(resharperBuildPath)

    doLast {
        val arguments = listOf(
            "build",
            "${resharperPluginPath.canonicalPath}/ReSharper.AWS.sln"
        )
        exec {
            executable = "dotnet"
            args = arguments
        }
    }
}

fun getNugetPackagesPath(): File {
    val sdkPath = tasks.setupDependencies.flatMap { it.idea }.map { it.classes }.get()
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

val cleanNetBuilds = tasks.register<Delete>("cleanNetBuilds") {
    group = protocolGroup
    description = "Clean up obj/ bin/ folders under ReSharper.AWS"
    delete(resharperBuildPath)
}

tasks.clean {
    dependsOn(cleanGenerateModels, cleanNetBuilds)
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

tasks.detekt {
    // Make sure kotlin code is generated before we execute detekt
    dependsOn(generateModels)
}

tasks.test {
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize = "1024m"

    outputs.cacheIf { false }
}

tasks.integrationTest {
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize = "1024m"

    // test detection is broken for tests inheriting from JB test framework: https://youtrack.jetbrains.com/issue/IDEA-278926
    setScanForTestClasses(false)
    include("**/*Test.class")
}
