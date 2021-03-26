// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import software.aws.toolkits.gradle.IdeVersions
import software.aws.toolkits.gradle.changelog.tasks.GenerateGithubChangeLog

val ideProfile = IdeVersions.ideProfile(project)
val toolkitVersion: String by project
val ktlintVersion: String by project

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

tasks.register<GenerateGithubChangeLog>("generateChangeLog") {
    changeLogFile.set(project.file("CHANGELOG.md"))
}

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
