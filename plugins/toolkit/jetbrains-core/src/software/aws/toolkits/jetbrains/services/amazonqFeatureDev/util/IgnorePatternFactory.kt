// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

private val baseIgnorePatterns = listOf(
    "\\.aws-sam",
    "\\.svn",
    "\\.hg/",
    "\\.rvm",
    "\\.git/",
    "\\.project",
    "\\.gem",
    "/\\.idea/",
    "\\.zip$",
    "\\.bin$",
    "\\.png$",
    "\\.jpg$",
    "\\.svg$",
    "\\.pyc$",
    "/license\\.txt$",
    "/License\\.txt$",
    "/LICENSE\\.txt$",
    "/license\\.md$",
    "/License\\.md$",
    "/LICENSE\\.md$",
).map { Regex(it) }

class IgnorePatternFactory {
    class PatternIgnorer(private val ignorePatterns: List<Regex>) {
        fun shouldIgnore(filePath: String): Boolean {
            return ignorePatterns.any { ip -> ip.containsMatchIn(filePath) }
        }
    }

    companion object {
        fun createFromFileName(fileName: String, projectRoot: VirtualFile?): PatternIgnorer {
            val file = if (projectRoot != null) File(projectRoot.path, fileName) else File(fileName)

            var allIgnorePatterns: List<Regex> = baseIgnorePatterns

            if (file.exists()) {
                allIgnorePatterns += file.readLines()
                    .filterNot { it.isBlank() || it.startsWith("#") }
                    .map { it.trim() }
                    .map { convertGitIgnorePatternToRegex(it) }
                    .map { Regex(it) }
            }

            return PatternIgnorer(allIgnorePatterns)
        }
    }
}

private fun convertGitIgnorePatternToRegex(pattern: String): String = pattern
    .replace(".", "\\.")
    .replace("*", ".*")
    .let { if (it.endsWith("/")) "$it?" else it } // Handle directory-specific patterns by optionally matching trailing slash
