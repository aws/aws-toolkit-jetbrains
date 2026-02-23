// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExtractFlareTask @Inject constructor(
    private val archiveOps: ArchiveOperations,
    private var fs: FileSystemOperations
) : DefaultTask() {
    @get:InputFiles
    abstract val zipFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun extract() {
        val destDir = outputDir.get().asFile
        destDir.deleteRecursively()
        destDir.mkdirs()

        zipFiles.filter { it.name.endsWith(".zip") }.forEach { zipFile ->
            logger.info("Extracting flare from $zipFile")
            fs.copy {
                outputDir.file(zipFile.parentFile.name).get().asFile.createNewFile()
                from(archiveOps.zipTree(zipFile)) {
                    include("*.js")
                    include("*.txt")
                }
                into(destDir)
            }
        }
    }
}
