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
        var hasError = false
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
                        LOG.warn(""""$filePath contains invalid message missing a '=': "$it"""")
                        null
                    }
                }
                .map { it.split("=").first() }
                .reduce { item1, item2 ->
                    if (item1 > item2) {
                        LOG.error("""$filePath is not sorted:"$item1" > "$item2"""")
                        hasError = true
                    }

                    item2
                }
            if (hasError) {
                throw GradleException("$filePath has one or more out of order items!")
            }
        }
    }

    private companion object {
        val LOG: Logger = Logging.getLogger(ValidateMessages::class.java)
    }
}
