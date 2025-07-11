// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.logging.Logging
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import software.aws.toolkits.gradle.intellij.IdeVersions
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import javax.inject.Inject
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinVersionEnum

/**
 * Only run the given block if this build is running within a CI system (e.g. GitHub actions, CodeBuild etc)
 */
fun Project.ciOnly(block: () -> Unit) {
    if (isCi()) {
        block()
    }
}

fun Project.isCi() : Boolean = providers.environmentVariable("CI").isPresent

fun Project.jvmTarget(): Provider<JavaVersion> = withCurrentProfileName {
    JavaVersion.VERSION_21
}

// https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#stdlib-miscellaneous
fun Project.kotlinTarget(): Provider<String> = withCurrentProfileName {
    when (it) {
        "2024.2" -> KotlinVersionEnum.KOTLIN_1_9
        "2024.3" -> KotlinVersionEnum.KOTLIN_2_0
        "2025.1" -> KotlinVersionEnum.KOTLIN_2_1
        else -> error("not set")
    }.version
}

fun<T : Any> Project.withCurrentProfileName(consumer: (String) -> T): Provider<T> {
    val name = IdeVersions.ideProfile(providers).map { it.name }
    return name.map {
        consumer(it)
    }
}

abstract class GitHashValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        return try {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                standardOutput = output
            }
            val currentShortHash = String(output.toByteArray(), Charset.defaultCharset())

            val isDirty = execOperations.exec {
                commandLine("git", "status", "-s")
                standardOutput = OutputStream.nullOutputStream()
            }.exitValue != 0

            buildString {
                append(currentShortHash)

                if (isDirty) {
                    append(".modified")
                }
            }
        } catch(e: Exception) {
            Logging.getLogger(GitHashValueSource::class.java).warn("Could not determine current commit", e)

            "unknownCommit"
        }
    }
}

fun Project.buildMetadata() = providers.of(GitHashValueSource::class.java) {}
