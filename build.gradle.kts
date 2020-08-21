// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.TestLoggerPlugin
import org.jetbrains.intellij.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.tasks.DownloadRobotServerPluginTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.ProductCode
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog
import software.aws.toolkits.gradle.findFolders
import software.aws.toolkits.gradle.getOrCreate
import software.aws.toolkits.gradle.intellij
import software.aws.toolkits.gradle.removeTask
import software.aws.toolkits.gradle.resources.ValidateMessages
import java.time.Instant

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
val remoteRobotVersion: String by project
val ideaPluginVersion: String by project
// publishing
val publishToken: String by project
val publishChannel: String by project

plugins {
    java
    id("de.undercouch.download") apply false
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
        val alternativeIde = System.getenv("ALTERNATIVE_IDE")
        if (alternativeIde != null) {
            if (File(alternativeIde).exists()) {
                intellij {
                    alternativeIdePath = System.getenv("ALTERNATIVE_IDE")
                }
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
        getOrCreate("integrationTest") {
            java.srcDir("it")
        }
    }
}

subprojects {
    parent?.let {
        group = it.group
        version = it.version
    } ?: throw IllegalStateException("Subproject $name parent is null!")

    apply(plugin = "java")
    apply(plugin = "idea")
    apply(plugin = "com.adarshr.test-logger")

    sourceSets {
        main {
            java.srcDirs(findFolders(project, "src", ideVersion))
            resources.srcDirs(findFolders(project, "resources", ideVersion))
        }
        test {
            java.srcDirs(findFolders(project, "tst", ideVersion))
            resources.srcDirs(findFolders(project, "tst-resources", ideVersion))
        }
        getOrCreate("integrationTest") {
            compileClasspath += main.get().output + test.get().output
            runtimeClasspath += main.get().output + test.get().output
            java.srcDirs(findFolders(project, "it", ideVersion))
            resources.srcDirs(findFolders(project, "it-resources", ideVersion))
        }
    }

    val testArtifacts by configurations.creating
    configurations.getByName("integrationTestImplementation").apply {
        extendsFrom(configurations.getByName("testImplementation"))
    }
    configurations.getByName("integrationTestRuntimeOnly").apply {
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

    plugins.withType<TestLoggerPlugin> {
        configure<TestLoggerExtension> {
            showFullStackTraces = true
            showStandardStreams = true
            showPassedStandardStreams = false
            showSkippedStandardStreams = true
            showFailedStandardStreams = true
        }
    }

    tasks.test {
        configure<JacocoTaskExtension> {
            // don't instrument sdk, icons, ktlint, etc.
            includes = listOf("software.aws.toolkits.*")
            excludes = listOf("software.aws.toolkits.ktlint.*")
        }

        reports {
            junitXml.isEnabled = true
            html.isEnabled = true
        }
    }

    plugins.withType<IdeaPlugin> {
        model.module.apply {
            sourceDirs.plusAssign(sourceSets.main.get().java.srcDirs)
            resourceDirs.plusAssign(sourceSets.main.get().resources.srcDirs)
            testSourceDirs.plusAssign(File("tst-$ideVersion"))
            testResourceDirs.plusAssign(File("tst-resources-$ideVersion"))

            sourceDirs.minusAssign(File("it"))
            testSourceDirs.plusAssign(File("it"))
            testSourceDirs.plusAssign(File("it-$ideVersion"))

            resourceDirs.minusAssign(File("it-resources"))
            testResourceDirs.plusAssign(File("it-resources"))
            testResourceDirs.plusAssign(File("it-resources-$ideVersion"))
        }
    }

    tasks.register<Test>("integrationTest") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs the integration tests."
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath

        configure<JacocoTaskExtension> {
            // don"t instrument sdk, icons, ktlint, etc.
            includes = listOf("software.aws.toolkits.*")
            excludes = listOf("software.aws.toolkits.ktlint.*")
        }

        project.plugins.withId("org.jetbrains.intellij") {
            systemProperty("log.dir", "${(project.extensions["intellij"] as org.jetbrains.intellij.IntelliJPluginExtension).sandboxDirectory}-test/logs")
        }

        systemProperty("testDataPath", project.rootDir.toPath().resolve("testdata").toString())

        mustRunAfter(tasks.test)
    }

    project.plugins.withId("org.jetbrains.intellij") {
        extensions.getByType<JacocoPluginExtension>().applyTo(tasks.getByName<RunIdeForUiTestTask>("runIdeForUiTests"))

        tasks.withType(DownloadRobotServerPluginTask::class.java) {
            this.version = remoteRobotVersion
        }

        tasks.withType(RunIdeForUiTestTask::class.java).all {
            systemProperty("robot-server.port", remoteRobotPort)
            systemProperty("ide.mac.file.chooser.native", "false")
            systemProperty("jb.consents.confirmation.enabled", "false")
            // This does some magic in EndUserAgreement.java to make it not show the privacy policy
            systemProperty("jb.privacy.policy.text", "<!--999.999-->")
            if (System.getenv("CI") != null) {
                systemProperty("aws.sharedCredentialsFile", "/tmp/.aws/credentials")
            }

            configure<JacocoTaskExtension> {
                destinationFile = file("$buildDir/jacoco/${Instant.now()}-jacocoUiTests.exec")
            }
        }
    }

    tasks.withType<KotlinCompile>().all {
        kotlinOptions.jvmTarget = "1.8"
    }

    // Force us to compile the integration tests even during check even though we don't run them
    tasks.named("check") {
        dependsOn.add(sourceSets.getByName("integrationTest").compileJavaTaskName)
    }

    val testJar = tasks.register<Jar>("testJar") {
        baseName = "${project.name}-test"
        from(sourceSets.test.get().output)
        from(sourceSets.getByName("integrationTest").output)
    }

    artifacts {
        add("testArtifacts", testJar)
    }

    // Remove the tasks added in by gradle-intellij-plugin so that we don"t publish/verify multiple times
    project.afterEvaluate {
        removeTask<PublishTask>()
        removeTask<VerifyPluginTask>()
        removeTask<BuildSearchableOptionsTask>()
    }
}

val ktlint: Configuration by configurations.creating

apply(plugin = "org.jetbrains.intellij")
apply(plugin = "toolkit-change-log")

intellij {
    version = ideVersions.ideSdkVersion(ProductCode.IC)
    pluginName = "aws-jetbrains-toolkit"
    updateSinceUntilBuild = false
    downloadSources = System.getenv("CI") == null
}

tasks.getByName<PrepareSandboxTask>("prepareSandbox") {
    tasks.findByPath(":jetbrains-rider:prepareSandbox")?.let {
        from(it)
    }
}

tasks.getByName<PublishTask>("publishPlugin") {
    token(publishToken)
    channels(if (publishChannel != null) publishChannel.split(",").map { it.trim() } else listOf())
}

tasks.register<GenerateGithubChangeLog>("generateChangeLog") {
    changeLogFile.set(project.file("CHANGELOG.md"))
}

val ktlintTask = tasks.register<JavaExec>("ktlint") {
    description = "Check Kotlin code style."
    classpath = configurations.getByName("ktlint")
    group = "verification"
    main = "com.pinterest.ktlint.Main"

    val isWindows = System.getProperty("os.name")?.toLowerCase()?.contains("windows") == true

    // Must be relative or else Windows will fail
    var toInclude = project.projectDir.toRelativeString(project.rootDir) + "/**/*.kt"
    var toExclude = File(project.projectDir, "jetbrains-rider").toRelativeString(project.rootDir) + "/**/*.Generated.kt"

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

    subprojects.forEach {
        additionalSourceDirs.from(it.sourceSets.main.get().java.srcDirs)
        sourceDirectories.from(it.sourceSets.main.get().java.srcDirs)
        classDirectories.from(it.sourceSets.main.get().output.classesDirs)
    }

    reports {
        html.isEnabled = true
        xml.isEnabled = true
    }
}

subprojects.forEach {
    coverageReport.get().mustRunAfter(it.tasks.withType(Test::class.java))
}

val check = tasks.getByName("check")
check.dependsOn(ktlintTask)
check.dependsOn(validateLocalizedMessages)
check.dependsOn(tasks.getByName("verifyPlugin"))
check.dependsOn(coverageReport)

// Workaround for runIde being defined in multiple projects, if we request the root project runIde, "alias" it to
// community edition
if (gradle.startParameter.taskNames.contains("runIde")) {
    // Only disable this if running from root project
    if (gradle.startParameter.projectDir == project.rootProject.rootDir || System.getProperty("idea.gui.tests.gradle.runner") != null) {
        println("Top level runIde selected, excluding sub-projects")
        gradle.taskGraph.whenReady {
            allTasks.forEach { it ->
                if (it.name == "runIde" && it.project != project(":jetbrains-core")) {
                    it.enabled = false
                }
            }
        }
    }
}

dependencies {
    implementation(project(":jetbrains-ultimate"))
    project.findProject(":jetbrains-rider")?.let {
        implementation(it)
    }

    ktlint("com.pinterest:ktlint:$ktlintVersion")
    ktlint(project(":ktlint-rules"))
}
