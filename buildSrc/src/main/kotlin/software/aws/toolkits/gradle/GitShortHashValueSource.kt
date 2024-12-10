// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.gradle

import org.eclipse.jgit.api.Git
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.slf4j.LoggerFactory
import java.io.File

abstract class GitShortHashValueSourceParameters : ValueSourceParameters {
    abstract val gitRootDir: Property<File>
}

abstract class GitShortHashValueSource : ValueSource<String, GitShortHashValueSourceParameters> {
    override fun obtain(): String =
        try {
            val git = Git.open(parameters.gitRootDir.get())
            val currentShortHash = git.repository.findRef("HEAD").objectId.abbreviate(7).name()
            val isDirty = git.status().call().hasUncommittedChanges()

            buildString {
                append(currentShortHash)

                if (isDirty) {
                    append(".modified")
                }
            }
        } catch(e: Exception) {
            logger.warn("Could not determine current commit", e)

            "unknownCommit"
        }

    companion object {
        private val logger = LoggerFactory.getLogger("GitShortHashValueSource")
    }
}
