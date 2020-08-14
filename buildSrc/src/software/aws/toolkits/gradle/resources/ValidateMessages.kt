// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle.resources

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

open class ValidateMessages : DefaultTask() {
    @InputFiles
    lateinit var paths: List<File>

    @TaskAction
    fun validateMessage() {
        paths.map {
            it.absolutePath to it.readLines()
        }.forEach { (filePath, fileLines) ->
            fileLines
                // filter out blank lines and comments
                .filter { it.isNotBlank() && it.trim().firstOrNull() != '#' }
                .mapNotNull {
                    if (it.contains("=")) {
                        it
                    } else {
                        LOG.warn(""""Invalid message in $filePath does not contain a '=': "$it"""")
                        null
                    }
                }
                .map { it.split("=").first() }
                .reduce { item1, item2 ->
                    if (item1 > item2) {
                        throw GradleException("""localization file $filePath is not sorted:"$item1" > "$item2"""")
                    }

                    item2
                }
        }
    }

    private companion object {
        val LOG: Logger = Logging.getLogger(ValidateMessages::class.java)
    }
}
