// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog

buildscript {
    dependencies {
        classpath("com.adarshr:gradle-test-logger-plugin:2.1.1")
    }
}

val ideProfile = IdeVersions.ideProfile(project)
val toolkitVersion: String by project
val kotlinVersion: String by project
val mockitoVersion: String by project
val mockitoKotlinVersion: String by project
val assertjVersion: String by project
val junitVersion: String by project
val remoteRobotPort: String by project
val ktlintVersion: String by project
val remoteRobotVersion: String by project

plugins {
    id("base")
    id("toolkit-changelog")
    id("de.undercouch.download") apply false
}

group = "software.aws.toolkits"
// please check changelog generation logic if this format is changed
version = "$toolkitVersion-${ideProfile.shortName}"

allprojects {
    repositories {
        mavenLocal()
        System.getenv("CODEARTIFACT_URL")?.let {
            println("Using CodeArtifact proxy: $it")
            maven {
                url = uri(it)
                credentials {
                    username = "aws"
                    password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
                }
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}


//    project.plugins.withId("org.jetbrains.intellij") {
//        extensions.getByType<JacocoPluginExtension>().applyTo(tasks.getByName<RunIdeForUiTestTask>("runIdeForUiTests"))

//        tasks.withType(RunIdeForUiTestTask::class.java).all {
//            systemProperty("robot-server.port", remoteRobotPort)
//            systemProperty("ide.mac.file.chooser.native", "false")
//            systemProperty("jb.consents.confirmation.enabled", "false")
//            // This does some magic in EndUserAgreement.java to make it not show the privacy policy
//            systemProperty("jb.privacy.policy.text", "<!--999.999-->")
//            // This only works on 2020.3+ FIX_WHEN_MIN_IS_203 remove this explanation
//            systemProperty("ide.show.tips.on.startup.default.value", false)
//
//            systemProperty("aws.telemetry.skip_prompt", "true")
//            systemProperty("aws.suppress_deprecation_prompt", true)
//            ciOnly {
//                systemProperty("aws.sharedCredentialsFile", "/tmp/.aws/credentials")
//            }
//
//            debugOptions {
//                enabled.set(true)
//                suspend.set(false)
//            }
//
//            configure<JacocoTaskExtension> {
//                setDestinationFile(File("$buildDir/jacoco/${Instant.now()}-jacocoUiTests.exec"))
//            }
//        }

//    val testJar = tasks.register<Jar>("testJar") {
//        archiveBaseName.set("${project.name}-test")
//        from(sourceSets.test.get().output)
//        from(sourceSets.getByName("integrationTest").output)
//    }
//
//    artifacts {
//        add("testArtifacts", testJar)
//    }

tasks.register<GenerateGithubChangeLog>("generateChangeLog") {
    changeLogFile.set(project.file("CHANGELOG.md"))
}
//
//val ktlint: Configuration by configurations.creating
//val ktlintTask = tasks.register<JavaExec>("ktlint") {
//    description = "Check Kotlin code style."
//    classpath = ktlint
//    group = "verification"
//    main = "com.pinterest.ktlint.Main"
//
//    enabled = false
//
//    val isWindows = System.getProperty("os.name")?.toLowerCase()?.contains("windows") == true
//
//    // Must be relative or else Windows will fail
//    var toInclude = project.projectDir.toRelativeString(project.rootDir) + "/**/*.kt"
//    var toExclude = File(project.projectDir, "jetbrains-rider").toRelativeString(project.rootDir) + "/**/*.Generated.kt"
//
//    if (isWindows) {
//        toInclude = toInclude.replace("/", "\\")
//        toExclude = toExclude.replace("/", "\\")
//    }
//
//    args = listOf("-v", toInclude, "!${toExclude}", "!/**/generated-src/**/*.kt")
//
//    inputs.files(fileTree(".") { include("**/*.kt") })
//    outputs.dirs("${project.buildDir}/reports/ktlint/")
//}

//val coverageReport = tasks.register<JacocoReport>("coverageReport") {
//    executionData.setFrom(fileTree(project.rootDir.absolutePath) { include("**/build/jacoco/*.exec") })
//
//    subprojects.forEach {
//        additionalSourceDirs.from(it.sourceSets.main.get().java.srcDirs)
//        sourceDirectories.from(it.sourceSets.main.get().java.srcDirs)
//        classDirectories.from(it.sourceSets.main.get().output.classesDirs)
//    }
//
//    reports {
//        html.isEnabled = true
//        xml.isEnabled = true
//    }
//}
//
//subprojects.forEach {
//    coverageReport.get().mustRunAfter(it.tasks.withType(Test::class.java))
//}

val coverageReport = tasks.register("coverageReport") {

}

tasks.check {
//    dependsOn(ktlintTask)
    dependsOn(coverageReport)
}

//dependencies {
//    ktlint("com.pinterest:ktlint:$ktlintVersion")
//    ktlint(project(":ktlint-rules"))
//}

tasks.register("runIde") {
    doFirst {
        throw GradleException("Use project specific runIde command, i.e. :jetbrains-core:runIde, :intellij:runIde")
    }
}
