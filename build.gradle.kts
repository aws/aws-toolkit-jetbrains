// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.resources.ValidateMessages

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
        mavenCentral()
        jcenter()
    }
    val kotlinVersion: String by project
    val ideaPluginVersion: String by project
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("gradle.plugin.org.jetbrains.intellij.plugins:gradle-intellij-plugin:$ideaPluginVersion")
        classpath("com.adarshr:gradle-test-logger-plugin:1.7.0")
    }
}

val ideVersions = IdeVersions(project)
val ideVersion = ideVersions.resolveShortenedIdeProfileName()
val toolkitVersion: String by project
val kotlinVersion: String by project
val mockitoVersion: String by project
val mockitoKotlinVersion: String by project
val assertjVersion: String by project
val junitVersion: String by project
val remoteRobotPort: String by project
val ktlintVersion: String by project

plugins {
    id("de.undercouch.download") version "4.1.1" apply false
    java
}

group = "software.aws.toolkits"
// please check changelog generation logic if this format is changed
version = "$toolkitVersion-$ideVersion".toString()

repositories {
    maven("https://www.jetbrains.com/intellij-repository/snapshots/")
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
    }

    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "java")
    apply(plugin = "jacoco")

    java.sourceCompatibility = JavaVersion.VERSION_1_8
    java.targetCompatibility = JavaVersion.VERSION_1_8

    tasks.withType(JavaExec::class.java) {
        systemProperty("aws.toolkits.enableTelemetry", false)
    }

    tasks.withType(RunIdeTask::class.java) {
        val ijExt = project.extensions.getByName("intellij") as org.jetbrains.intellij.IntelliJPluginExtension
        val alternativeIde = System.getenv("ALTERNATIVE_IDE")
        if (alternativeIde != null) {
            if (File(alternativeIde).exists()) {
                ijExt.alternativeIdePath = System.getenv("ALTERNATIVE_IDE")
            } else {
                throw GradleException("ALTERNATIVE_IDE path not found $alternativeIde ${if (alternativeIde.endsWith("/")) "remove the trailing slash" else ""}")
            }
        }
    }

    configurations {
        runtimeClasspath {
            exclude(group = "org.slf4j")
            exclude(group = "org.jetbrains.kotlin")
            exclude(group = "org.jetbrains.kotlinx")
            exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        }
    }
}

// Kotlin plugin seems to be bugging out when there are no kotlin sources
configure(subprojects.filter { it.name != "telemetry-client" }) {
    apply(plugin = "kotlin")

    sourceSets {
        sourceSets.getByName("integrationTest") {
            java.srcDir("it")
        }
    }
}

subprojects {
    group = parent!!.group
    version = parent!!.version

    apply(plugin = "java")
    apply(plugin = "idea")
    apply(plugin = "com.adarshr.test-logger")

    sourceSets {
        main.get().java.srcDirs(findFolders(project, "src", ideVersion))
        main.get().resources.srcDirs(findFolders(project, "resources", ideVersion))
        test.get().java.srcDirs(findFolders(project, "tst", ideVersion))
        test.get().resources.srcDirs(findFolders(project, "tst-resources", ideVersion))
        create("integrationTest") {
            compileClasspath += main.get().output + test.get().output
            runtimeClasspath += main.get().output + test.get().output
            java.srcDirs(findFolders(project, "it", ideVersion))
            resources.srcDirs(findFolders(project, "it-resources", ideVersion))
        }
    }

    val testArtifacts by configurations.creating
    val integrationTestImplementation by configurations.creating {
        extendsFrom(configurations.getByName("testImplementation"))
    }
    val integrationTestRuntimeOnly by configurations.creating {
        extendsFrom(configurations.getByName("testRuntimeOnly"))
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
        implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:$mockitoKotlinVersion")
        testImplementation("org.mockito:mockito-core:$mockitoVersion")
        testImplementation("org.assertj:assertj-core:$assertjVersion")
        testImplementation("junit:junit:$junitVersion")
    }

    testlogger {
        showFullStackTraces = true
        showStandardStreams = true
        showPassedStandardStreams = false
        showSkippedStandardStreams = true
        showFailedStandardStreams = true
    }

    test {
        jacoco {
            // don"t instrument sdk, icons, ktlint, etc.
            includes = ["software.aws.toolkits.*"]
            excludes = ["software.aws.toolkits.ktlint.*"]
        }

        reports {
            junitXml.enabled = true
            html.enabled = true
        }
    }

    idea {
        module {
            sourceDirs += sourceSets.main.java.srcDirs
            resourceDirs += sourceSets.main.resources.srcDirs
            testSourceDirs += file("tst-$ideVersion")
            testResourceDirs += file("tst-resources-$ideVersion")

            sourceDirs -= file("it")
            testSourceDirs += file("it")
            testSourceDirs += file("it-$ideVersion")

            resourceDirs -= file("it-resources")
            testResourceDirs += file("it-resources")
            testResourceDirs += file("it-resources-$ideVersion")
        }
    }

    tasks.register<Test>("integrationTest") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs the integration tests."
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath

        jacoco {
            // don"t instrument sdk, icons, ktlint, etc.
            includes = ["software.aws.toolkits.*"]
            excludes = ["software.aws.toolkits.ktlint.*"]
        }

        project.plugins.withId("org.jetbrains.intellij") {
            systemProperty("log.dir", "${project.intellij.sandboxDirectory}-test/logs")
        }

        mustRunAfter = tasks.test
    }

    project.plugins.withId("org.jetbrains.intellij") {
        downloadRobotServerPlugin.version = remoteRobotVersion

        tasks.withType(org.jetbrains.intellij.tasks.RunIdeForUiTestTask).all {
            systemProperty("robot-server.port", remoteRobotPort)
            systemProperty("ide.mac.file.chooser.native", "false")
            systemProperty("jb.consents.confirmation.enabled", "false")
            // This does some magic in EndUserAgreement.java to make it not show the privacy policy
            systemProperty("jb.privacy.policy.text", "<!--999.999-->")
            if (System.getenv("CI") != null) {
                systemProperty("aws.sharedCredentialsFile", "/tmp/.aws/credentials")
            }
        }

        jacoco.applyTo(runIdeForUiTests)
    }

    tasks.withType(KotlinCompile).all {
        kotlinOptions.jvmTarget = "1.8"
    }

    // Force us to compile the integration tests even during check even though we don't run them
    check.dependsOn(integrationTestClasses)

    val testJar = tasks.register<Jar>("testJar") {
        baseName = "${project.name}-test"
        from(sourceSets.test.output)
        from(sourceSets.integrationTest.output)
    }

    artifacts {
        testArtifacts = testJar
    }

    // Remove the tasks added in by gradle-intellij-plugin so that we don"t publish/verify multiple times
    project.afterEvaluate {
        removeTask(tasks, org.jetbrains.intellij.tasks.PublishTask)
        removeTask(tasks, org.jetbrains.intellij.tasks.VerifyPluginTask)
        removeTask(tasks, org.jetbrains.intellij.tasks.BuildSearchableOptionsTask)
    }
}

val ktlint by configurations.creating

fun removeTask(tasks: TaskContainer, takeType: Class<Task>) {
    tasks.withType(takeType).configureEach {
        setEnabled(false)
    }
}

apply(plugin = "org.jetbrains.intellij")
apply(plugin = "toolkit-change-log")

intellij {
    version = ideSdkVersion("IC")
    pluginName = "aws-jetbrains-toolkit"
    updateSinceUntilBuild = false
    downloadSources = System.getenv("CI") == null
}

prepareSandbox {
    tasks.findByPath(":jetbrains-rider:prepareSandbox")?.collect {
        from(it)
    }
}

publishPlugin {
    token = publishToken
    channels = if (publishChannel != null) publishChannel.split(",").map { it.trim() } else []
}

tasks.register<GenerateGithubChangeLog>("generateChangeLog") {
    changeLogFile = project.file("CHANGELOG.md")
}

val ktlintTask = tasks.register<JavaExec>("ktlint") {
    description = "Check Kotlin code style."
    classpath = configurations.getByName("ktlint")
    group = "verification"
    main = "com.pinterest.ktlint.Main"

    val isWindows = System.getProperty("os.name")?.toLowerCase()?.contains("windows") == true

    var toInclude = project.rootDir.relativePath(project.projectDir) + "/**/*.kt"
    var toExclude = project.rootDir.relativePath(File(project.projectDir, "jetbrains-rider")) + "/**/*.Generated.kt"

    if (isWindows) {
        toInclude = toInclude.replace("/", "\\")
        toExclude = toExclude.replace("/", "\\")
    }

    args = listOf("-v", toInclude, "!${toExclude}", "!/**/generated-src/**/*.kt")

    inputs.files(fileTree(".") { include("**/*.kt") })
    outputs.dirs("${project.buildDir}/reports/ktlint/")
}

val validateLocalizedMessages = tasks.register<ValidateMessages>("validateLocalizedMessages") {
    paths.set(listOf("${project.rootDir}/resources/resources/software/aws/toolkits/resources/localized_messages.properties"))
}

val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    executionData.setFrom(fileTree(project.rootDir.absolutePath) { include("**/build/jacoco/*.exec") })

    additionalSourceDirs.from(subprojects.sourceSets.main.java.srcDirs)
    sourceDirectories.from(subprojects.sourceSets.main.java.srcDirs)
    classDirectories.from(subprojects.sourceSets.main.output.classesDirs)

    reports {
        html.isEnabled = true
        xml.isEnabled = true
    }
}

subprojects.forEach {
    coverageReport.mustRunAfter(it.tasks.withType(Test))
}

check.dependsOn(ktlintTask)
check.dependsOn(validateLocalizedMessages)
check.dependsOn(verifyPlugin)
check.dependsOn(coverageReport)

// Workaround for runIde being defined in multiple projects, if we request the root project runIde, "alias" it to
// community edition
if (gradle.startParameter.taskNames.contains("runIde")) {
    // Only disable this if running from root project
    if (gradle.startParameter.projectDir == project.rootProject.rootDir || System.getProperty("idea.gui.tests.gradle.runner") != null
    ) {
        println("Top level runIde selected, excluding sub-projects")
        gradle.taskGraph.whenReady({ graph ->
            graph.allTasks.forEach { it ->
                if (it.name == "runIde" && it.project != project(":jetbrains-core")) {
                    it.enabled = false
                }
            }
        })
    }
    // Else required because this is an expression
} else {
}

dependencies {
    implementation(project(":jetbrains-ultimate"))
    project.findProject(":jetbrains-rider")?.map {
        implementation(it)
    }

    ktlint("com.pinterest:ktlint:$ktlintVersion")
    ktlint(project(":ktlint-rules"))
}
