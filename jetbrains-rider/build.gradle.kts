// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
import com.jetbrains.rd.generator.gradle.RdgenParams
import com.jetbrains.rd.generator.gradle.RdgenTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask

buildscript {
    val rdGenVersion: groovy.lang.Closure<String> by project
    val rdversion = rdGenVersion()
    project.extra["rd_version"] = rdversion

    logger.info("Using rd-gen: $rdversion")

    repositories {
        maven("https://www.myget.org/F/rd-snapshots/maven/")
        mavenCentral()
    }

    dependencies {
        classpath("com.jetbrains.rd:rd-gen:$rdversion")
    }
}

// IntellijVerison things
val rdGenVersion: groovy.lang.Closure<String> by project

fun Project.intellij(): org.jetbrains.intellij.IntelliJPluginExtension = extensions["intellij"] as org.jetbrains.intellij.IntelliJPluginExtension

val resharperPluginPath = File(projectDir, "ReSharper.AWS")
val resharperBuildPath = File(project.buildDir, "dotnetBuild")

val buildConfiguration = project.extra.properties["BuildConfiguration"] ?: "Debug" // TODO: Do we ever want to make a release build?

plugins {
    "org.jetbrains.intellij"
    "com.jetbrains.rdgen"
}

// Protocol
val protocolGroup = "protocol"

val csDaemonGeneratedOutput = File(resharperPluginPath, "src/AWS.Daemon/Protocol")
val csPsiGeneratedOutput = File(resharperPluginPath, "src/AWS.Psi/Protocol")
val csAwsSettingGeneratedOutput = File(resharperPluginPath, "src/AWS.Settings/Protocol")
val csAwsProjectGeneratedOutput = File(resharperPluginPath, "src/AWS.Project/Protocol")
val riderGeneratedSources = "$buildDir/generated-src/software/aws/toolkits/jetbrains/protocol"

val modelDir = File(projectDir, "protocol/model")
val rdgenDir = File("${project.buildDir}/rdgen/")

rdgenDir.mkdirs()

tasks.register<RdgenTask>("generateDaemonModel") {
    val daemonModelSource = File(modelDir, "daemon").canonicalPath
    val ktOutput = File(riderGeneratedSources, "DaemonProtocol")

    inputs.property("rdgen", rdGenVersion())
    inputs.dir(daemonModelSource)
    outputs.dirs(ktOutput, csDaemonGeneratedOutput)

    // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
    // intellij SDK, which is extracted in afterEvaluate
    configure<RdgenParams> {
        verbose = true
        hashFolder = rdgenDir.toString()

        logger.info("Configuring rdgen params")

        logger.info("Calculating classpath for rdgen, intellij.ideaDependency is: ${project.intellij().ideaDependency}")
        val sdkPath = project.intellij().ideaDependency.classes
        val rdLibDirectory = File("$sdkPath/lib/rd").canonicalFile
        classpath("$rdLibDirectory/rider-model.jar")

        sources(daemonModelSource)
        packages = "protocol.model.daemon"

        generator {
            language = "kotlin"
            transform = "asis"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "com.jetbrains.rider.model"
            directory = "$ktOutput"
        }

        generator {
            language = "csharp"
            transform = "reversed"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "JetBrains.Rider.Model"
            directory = "$csDaemonGeneratedOutput"
        }
    }
}

tasks.register<RdgenTask>("generatePsiModel") {
    def psiModelSource = new File(modelDir, "psi").canonicalPath
    def ktOutput = new File(riderGeneratedSources, "PsiProtocol")

    inputs.property("rdgen", rdGenVersion())
    inputs.dir(psiModelSource)
    outputs.dirs(ktOutput, csPsiGeneratedOutput)

    // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
    // intellij SDK, which is extracted in afterEvaluate
    configure<RdgenParams> {
        verbose = true
        hashFolder = rdgenDir

        logger.info("Configuring rdgen params")
        classpath {
            logger.info("Calculating classpath for rdgen, intellij.ideaDependency is: ${intellij.ideaDependency}")
            def sdkPath = intellij.ideaDependency.classes
                def rdLibDirectory = new File(sdkPath, "lib/rd").canonicalFile

            "$rdLibDirectory/rider-model.jar"
        }
        sources psiModelSource
            packages = "protocol.model.psi"

        generator {
            language = "kotlin"
            transform = "asis"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "com.jetbrains.rider.model"
            directory = "$ktOutput"
        }

        generator {
            language = "csharp"
            transform = "reversed"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "JetBrains.Rider.Model"
            directory = "$csPsiGeneratedOutput"
        }
    }
}

task generateAwsSettingModel(type: tasks.getByName("rdgen").class) {
    def settingModelSource = new File(modelDir, "setting").canonicalPath
    def ktOutput = new File(riderGeneratedSources, "AwsSettingsProtocol")

    inputs.property("rdgen", rdGenVersion())
    inputs.dir(settingModelSource)
    outputs.dirs(ktOutput, csAwsSettingGeneratedOutput)

    // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
    // intellij SDK, which is extracted in afterEvaluate
    params {
        verbose = true
        hashFolder = rdgenDir

        logger.info("Configuring rdgen params")
        classpath {
            logger.info("Calculating classpath for rdgen, intellij.ideaDependency is: ${intellij.ideaDependency}")
            def sdkPath = intellij.ideaDependency.classes
                def rdLibDirectory = new File(sdkPath, "lib/rd").canonicalFile

            "$rdLibDirectory/rider-model.jar"
        }
        sources settingModelSource
            packages = "protocol.model.setting"

        generator {
            language = "kotlin"
            transform = "asis"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "com.jetbrains.rider.model"
            directory = "$ktOutput"
        }

        generator {
            language = "csharp"
            transform = "reversed"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "JetBrains.Rider.Model"
            directory = "$csAwsSettingGeneratedOutput"
        }
    }
}

task generateAwsProjectModel(type: tasks.getByName("rdgen").class) {
    def projectModelSource = new File(modelDir, "project").canonicalPath
    def ktOutput = new File(riderGeneratedSources, "AwsProjectProtocol")

    inputs.property("rdgen", rdGenVersion())
    inputs.dir(projectModelSource)
    outputs.dirs(ktOutput, csAwsProjectGeneratedOutput)

    // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
    // intellij SDK, which is extracted in afterEvaluate
    params {
        verbose = true
        hashFolder = rdgenDir

        logger.info("Configuring rdgen params")
        classpath {
            logger.info("Calculating classpath for rdgen, intellij.ideaDependency is: ${intellij.ideaDependency}")
            def sdkPath = intellij.ideaDependency.classes
                def rdLibDirectory = new File(sdkPath, "lib/rd").canonicalFile

            "$rdLibDirectory/rider-model.jar"
        }
        sources projectModelSource
            packages = "protocol.model.project"

        generator {
            language = "kotlin"
            transform = "asis"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "com.jetbrains.rider.model"
            directory = "$ktOutput"
        }

        generator {
            language = "csharp"
            transform = "reversed"
            root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
            namespace = "JetBrains.Rider.Model"
            directory = "$csAwsProjectGeneratedOutput"
        }
    }
}

task generateModels {
    group = protocolGroup
    description = "Generates protocol models"

    dependsOn generateDaemonModel, generatePsiModel, generateAwsSettingModel, generateAwsProjectModel
}

task cleanGenerateModels {
    group = protocolGroup
    description = "Clean up generated protocol models"

    dependsOn cleanGenerateDaemonModel, cleanGeneratePsiModel, cleanGenerateAwsSettingModel, cleanGenerateAwsProjectModel
}
project.tasks.clean.dependsOn(cleanGenerateModels)

// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

def backendGroup = "backend"

task prepareBuildProps {
    def riderSdkVersionPropsPath = new File(resharperPluginPath, "RiderSdkPackageVersion.props")
    group = backendGroup

    inputs.property("riderNugetSdkVersion", riderNugetSdkVersion())
    outputs.file(riderSdkVersionPropsPath)

    doLast {
        def riderSdkVersion = riderNugetSdkVersion()
        def configText = """<Project>
  <PropertyGroup>
    <RiderSDKVersion>[$riderSdkVersion]</RiderSDKVersion>
  </PropertyGroup>
</Project>
"""
        riderSdkVersionPropsPath.write(configText)
    }
}

task prepareNuGetConfig {
    group = backendGroup

    def nugetConfigPath = new File(projectDir, "NuGet.Config")

    inputs.property("rdVersion", ideSdkVersion("RD"))
    outputs.file(nugetConfigPath)

    doLast {
        def nugetPath = getNugetPackagesPath()
        def configText = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="resharper-sdk" value="${nugetPath}" />
  </packageSources>
</configuration>
"""
        nugetConfigPath.write(configText)
    }
}

task buildReSharperPlugin {
    group = backendGroup
    description = "Builds the full ReSharper backend plugin solution"
    dependsOn generateModels, prepareBuildProps, prepareNuGetConfig

    inputs.dir(resharperPluginPath)
    outputs.dir(resharperBuildPath)

    outputs.files({
        fileTree(file("${resharperPluginPath.absolutePath}/src")).matching {
            include "**/bin/Debug/**/AWS*.dll"
            include "**/bin/Debug/**/AWS*.pdb"
        }.collect()
    })

    doLast {
        def arguments = ["build"]
        arguments << "${resharperPluginPath.canonicalPath}/ReSharper.AWS.sln"
        arguments << "/p:DefineConstants=\"PROFILE_${resolveIdeProfileName().replace(".", "_")}\""
        exec {
            executable = "dotnet"
            args = arguments
        }
    }
}

project.tasks.clean.dependsOn(cleanPrepareBuildProps, cleanPrepareNuGetConfig, cleanBuildReSharperPlugin)

private File getNugetPackagesPath() {
    def sdkPath = intellij.ideaDependency.classes
        println("SDK path: $sdkPath")

    // 2019
    def riderSdk = new File(sdkPath, "lib/ReSharperHostSdk")
    // 2020.1
    if (!riderSdk.exists()) {
        riderSdk = new File(sdkPath, "lib/DotNetSdkForRdPlugins")
    }

    println("NuGet packages: $riderSdk")
    if (!riderSdk.isDirectory()) throw new IllegalStateException("${riderSdk} does not exist or not a directory")

    return riderSdk
}

dependencies {
    compile project(":jetbrains-core")
    testImplementation project(path: ":jetbrains-core", configuration: "testArtifacts")
}

ext.riderGeneratedSources = "$buildDir/generated-src"

sourceSets {
    main.java.srcDir riderGeneratedSources
}

intellij {
    def parentIntellijTask = project(":jetbrains-core").intellij
    version ideSdkVersion("RD")
    pluginName parentIntellijTask.pluginName
    updateSinceUntilBuild parentIntellijTask.updateSinceUntilBuild

    // Workaround for https://youtrack.jetbrains.com/issue/IDEA-179607
    def extraPlugins = [ "rider-plugins-appender" ]
    plugins = idePlugins("RD") + extraPlugins

    // Disable downloading source to avoid issues related to Rider SDK naming that is missed in Idea
    // snapshots repository. The task is failed because if is unable to find related IC sources.
    downloadSources = false
    instrumentCode = false
}

def resharperParts = [
    "AWS.Daemon",
    "AWS.Localization",
    "AWS.Project",
    "AWS.Psi",
    "AWS.Settings"
]

// Tasks:
//
// `buildPlugin` depends on `prepareSandbox` task and then zips up the sandbox dir and puts the file in rider/build/distributions
// `runIde` depends on `prepareSandbox` task and then executes IJ inside the sandbox dir
// `prepareSandbox` depends on the standard Java `jar` and then copies everything into the sandbox dir

tasks.withType(PrepareSandboxTask).configureEach {
    dependsOn buildReSharperPlugin

    def files = resharperParts.collect {"$resharperBuildPath/bin/$it/$buildConfiguration/${it}.dll"} +
        resharperParts.collect {"$resharperBuildPath/bin/$it/$buildConfiguration/${it}.pdb"}
    from(files, {
        into("${intellij.pluginName}/dotnet")
    })
}

compileKotlin.dependsOn(generateModels)

test {
    systemProperty("log.dir", "${project.intellij.sandboxDirectory}-test/logs")
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
    maxHeapSize("1024m")
}

tasks.integrationTest {
    useTestNG()
    environment("LOCAL_ENV_RUN", true)
}

jar {
    archiveBaseName.set("aws-intellij-toolkit-rider")
}
