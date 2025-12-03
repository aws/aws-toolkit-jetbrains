// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.changelog.tasks

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import software.aws.toolkits.gradle.changelog.ChangeLogGenerator
import software.aws.toolkits.gradle.changelog.ChangeLogWriter
import software.aws.toolkits.gradle.changelog.JetBrainsWriter
import software.aws.toolkits.gradle.changelog.GithubWriter

// TODO(repo-split): Amazon Q repo keeps these classes

open class GenerateAmazonQPluginChangeLog : AmazonQChangeLogTask() {
    @Input
    @Optional
    val repoUrl: Provider<String?> = project.objects.property(String::class.java).convention("https://github.com/aws/aws-toolkit-jetbrains")

    @Input
    val includeUnreleased: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    @OutputFile
    val changeLogFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generate() {
        val writer = JetBrainsWriter(changeLogFile.get().asFile, repoUrl.get())
        val generator = ChangeLogGenerator(listOf(writer), logger)
        if (includeUnreleased.get()) {
            val unreleasedEntries = nextReleaseDirectory.jsonFiles().files
            if (unreleasedEntries.isNotEmpty()) {
                generator.addUnreleasedChanges(unreleasedEntries.map { it.toPath() })
            }
        }
        generator.addReleasedChanges(changesDirectory.jsonFiles().map { it.toPath() })
        generator.close()
    }
}

open class GenerateAmazonQGithubChangeLog : AmazonQChangeLogTask() {
    @Input
    @Optional
    val repoUrl: Provider<String?> = project.objects.property(String::class.java).convention("https://github.com/aws/aws-toolkit-jetbrains")

    @Input
    val includeUnreleased: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    @OutputFile
    val changeLogFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generate() {
        val writer = GithubWriter(changeLogFile.get().asFile.toPath(), repoUrl.get())
        val generator = ChangeLogGenerator(listOf(writer), logger)
        if (includeUnreleased.get()) {
            val unreleasedEntries = nextReleaseDirectory.jsonFiles().files
            if (unreleasedEntries.isNotEmpty()) {
                generator.addUnreleasedChanges(unreleasedEntries.map { it.toPath() })
            }
        }
        generator.addReleasedChanges(changesDirectory.jsonFiles().map { it.toPath() })
        generator.close()
        git?.stage(changeLogFile.get().asFile)
    }
}
