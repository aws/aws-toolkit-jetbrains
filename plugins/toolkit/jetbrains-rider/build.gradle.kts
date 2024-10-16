// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenPlugin
import com.jetbrains.rd.generator.gradle.RdGenTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.com.intellij.openapi.util.SystemInfo
import software.aws.toolkits.gradle.intellij.IdeFlavor
import software.aws.toolkits.gradle.intellij.IdeVersions
import java.nio.file.Path

buildscript {
    // Cannot be removed or else it will fail to compile
    @Suppress("RemoveRedundantQualifierName")
    val rdversion = software.aws.toolkits.gradle.intellij.IdeVersions.ideProfile(project).rider.rdGenVersion

    println("Using rd-gen: $rdversion")

    dependencies {
        if (rdversion.contains("pre")) {
            classpath(fileTree("bin/$rdversion"))
        } else {
            classpath("com.jetbrains.rd:rd-gen:$rdversion")
        }
    }
}

val ideProfile = IdeVersions.ideProfile(project)

plugins {
    id("toolkit-kotlin-conventions")
    id("toolkit-testing")
    id("toolkit-intellij-subplugin")
    id("toolkit-integration-testing")
}

intellijToolkit {
    ideFlavor.set(IdeFlavor.RD)
}

sourceSets {
    main {
        java.srcDirs(layout.buildDirectory.dir("generated-src"))
    }
}

dependencies {
    intellijPlatform {
        localPlugin(project(":plugin-core"))
        testFramework(TestFrameworkType.Bundled)

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1774
        when (providers.gradleProperty("ideProfileName").get()) {
            "2023.3", "2024.1" -> {}
            else -> {
                bundledModule("intellij.rider")
            }
        }
    }

    implementation(project(":plugin-toolkit:jetbrains-core"))

    testImplementation(project(path = ":plugin-toolkit:jetbrains-core", configuration = "testArtifacts"))
    testImplementation(testFixtures(project(":plugin-core:jetbrains-community")))
}

/**
 * RESHARPER
 */
// Not published to gradle plugin portal, use old syntax
apply<RdGenPlugin>()
tasks.register<RdGenTask>("generateModels")

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

// gradle recommends keeping this lazy as long as possible https://docs.gradle.org/current/userguide/upgrading_version_8.html#project_builddir
val nonLazyBuildDir = layout.buildDirectory.get().asFile
val csDaemonGeneratedOutput = File(resharperPluginPath, "src/AWS.Daemon/Protocol")
val csPsiGeneratedOutput = File(resharperPluginPath, "src/AWS.Psi/Protocol")
val csAwsSettingsGeneratedOutput = File(resharperPluginPath, "src/AWS.Settings/Protocol")
val csAwsProjectGeneratedOutput = File(resharperPluginPath, "src/AWS.Project/Protocol")

val riderGeneratedSources = File("$nonLazyBuildDir/generated-src/software/aws/toolkits/jetbrains/protocol")

val modelDir = File(projectDir, "protocol/model")
val rdgenDir = File("$nonLazyBuildDir/rdgen/")

rdgenDir.mkdirs()

// https://github.com/JetBrains/resharper-unity/blob/master/rider/build.gradle.kts
val rdLibDirectory: () -> File = { file(intellijPlatform.platformPath.resolve("lib/rd/")) }

val rdModelJarFile: File by lazy {
    val jarFile = File(rdLibDirectory(), "rider-model.jar").canonicalFile
    assert(jarFile.isFile)
    return@lazy jarFile
}

configure<RdGenExtension> {
    verbose = true
    hashFolder = rdgenDir.toString()

    classpath({
        rdModelJarFile
    })

    sources(projectDir.resolve("protocol/model"))
    packages = "model"
}

// TODO: migrate to official rdgen gradle plugin https://www.jetbrains.com/help/resharper/sdk/Rider.html#plugin-project-jvm
val generateModels = tasks.named<JavaExec>("generateModels") {
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
val codeArtifactNugetUrl: Provider<String> = providers.environmentVariable("CODEARTIFACT_NUGET_URL")
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

    val nugetConfigPath = File(projectDir, "NuGet.Config")
    // FIX_WHEN_MIN_IS_211 remove the projectDir one above
    val nugetConfigPath211 = Path.of(projectDir.absolutePath, "testData", "NuGet.config").toFile()

    inputs.property("rdVersion", ideProfile.rider.sdkVersion)
    outputs.files(nugetConfigPath, nugetConfigPath211)

    doLast {
        val nugetPath = getNugetPackagesPath()
        val codeArtifactConfigText = """<?xml version="1.0" encoding="utf-8"?>
  <configuration>
    <packageSources> 
    ${
            if (codeArtifactNugetUrl.isPresent) {
                """
       |   <clear /> 
       |   <add key="codeartifact-nuget" value="${codeArtifactNugetUrl.get()}v3/index.json" />
        """.trimMargin("|")
            } else {
                ""
            }
        }
    </packageSources>
  </configuration>
"""
        val configText = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="resharper-sdk" value="$nugetPath" />
  </packageSources>
</configuration>
"""
        nugetConfigPath.writeText(codeArtifactConfigText)
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
            "--verbosity",
            "normal",
            "${resharperPluginPath.canonicalPath}/ReSharper.AWS.sln"
        )
        exec {
            executable = "dotnet"
            args = arguments
        }
    }
}

fun getNugetPackagesPath(): File {
    val sdkPath = intellijPlatform.platformPath
    println("SDK path: $sdkPath")

    val riderSdk = sdkPath.resolve("lib").resolve("DotNetSdkForRdPlugins").toAbsolutePath().toFile()

    println("NuGet packages: $riderSdk")
    if (!riderSdk.isDirectory) error("$riderSdk does not exist or not a directory")

    return riderSdk
}

val resharperDlls = configurations.create("resharperDlls") {
    isCanBeResolved = false
}

val resharperDllsDir = tasks.register<Sync>("resharperDllsDir") {
    from(buildReSharperPlugin) {
        include("**/bin/**/$buildConfiguration/**/AWS*.dll")
        include("**/bin/**/$buildConfiguration/**/AWS*.pdb")
        // TODO: see if there is better way to do this
        exclude("**/AWSSDK*")
    }
    into("$nonLazyBuildDir/$name")

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

intellijPlatform {
    // kotlin and .NET parts of the plugin need to be in the same plugin base directroy
    projectName = "aws-toolkit-jetbrains"
}

tasks.withType<PrepareSandboxTask>().configureEach {
    dependsOn(resharperDllsDir)

    intoChild(intellijPlatform.projectName.map { "$it/dotnet" })
        .from(resharperDllsDir)
}

tasks.compileKotlin {
    dependsOn(generateModels)
}

tasks.withType<Detekt>().configureEach {
    // Make sure kotlin code is generated before we execute detekt
    dependsOn(generateModels)
}

tasks.integrationTest {
    // linux: computeSystemScaleFactor "Must be precomputed"
    systemProperty("hidpi", false)
}

tasks.test {
    if (SystemInfo.isWindows) {
        // extremely flaky
        filter.excludeTestsMatching("software.aws.toolkits.jetbrains.services.lambda.dotnet.LambdaGutterMarkHighlightingTest*")
    }

    // On Windows, complains that the computeSystemScaleFactor "Must be precomputed"
    systemProperty("hidpi", false)

    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize = "1024m"
}

tasks.integrationTest {
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize = "1024m"

    // test detection is broken for tests inheriting from JB test framework: https://youtrack.jetbrains.com/issue/IDEA-278926
    setScanForTestClasses(false)
    include("**/*Test.class")
}

// fix implicit dependency on generated source
tasks.withType<DetektCreateBaselineTask>().configureEach {
    dependsOn(generateModels)
}

configurations.all {
    if (name.contains("detekt")) {
        return@all
    }

    // test runner not happy with coroutines, but not clear where it's coming from:
    //   java.lang.Throwable: Thread context was already set: InstalledThreadContext(snapshot=null, context=EmptyCoroutineContext).
    //   Most likely, you are using 'runBlocking' instead of 'runBlockingCancellable' somewhere in the asynchronous stack.
    exclude("org.jetbrains.kotlinx")
}
