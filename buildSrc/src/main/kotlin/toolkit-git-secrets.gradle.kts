// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import de.undercouch.gradle.tasks.download.Download
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("de.undercouch.download")
}

val downloadGitSecrets = tasks.register<Download>("downloadGitSecrets") {
    src("https://raw.githubusercontent.com/awslabs/git-secrets/master/git-secrets")
    dest(layout.buildDirectory.file("git-secrets"))
    onlyIfModified(true)
    useETag(true)
}

val gitSecretsRegister = tasks.register<Exec>("gitSecretsRegister") {
    dependsOn(downloadGitSecrets)
    workingDir(rootDir)
    val path = "${layout.buildDirectory.get().asFile}${File.pathSeparator}"
    environment = environment.apply { replace("PATH", path + getOrDefault("PATH", "")) }
    commandLine("/bin/sh", "${layout.buildDirectory.get().asFile}/git-secrets", "--register-aws")
}

val gitSecretsAllowDummy = tasks.register<Exec>("gitSecretsAllowDummy") {
    dependsOn(gitSecretsRegister)
    workingDir(rootDir)
    commandLine("git", "config", "--add", "secrets.allowed", "123456789012")
}

val gitSecrets = tasks.register<Exec>("gitSecrets") {
    onlyIf {
        !DefaultNativePlatform.getCurrentOperatingSystem().isWindows
    }
    dependsOn(gitSecretsAllowDummy)
    workingDir(rootDir)
    val path = "${layout.buildDirectory.get().asFile}${File.pathSeparator}"
    environment = environment.apply { replace("PATH", path + getOrDefault("PATH", "")) }
    commandLine("/bin/sh", "${layout.buildDirectory.get().asFile}/git-secrets", "--scan")
}

tasks.findByName("check")?.let {
    it.dependsOn(gitSecrets)
}
