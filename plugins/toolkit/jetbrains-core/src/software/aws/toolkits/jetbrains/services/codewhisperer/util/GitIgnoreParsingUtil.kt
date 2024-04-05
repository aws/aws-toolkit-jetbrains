// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class GitIgnoreParsingUtil(private val projectRootPath: String) {

    private val parsedGitIgnorePatterns: List<String>
    init {
        parsedGitIgnorePatterns = parseGitIgnore()
    }

    private val ignorePatterns = listOf(
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

    fun ignoreFile(file: VirtualFile): Boolean {
        val ignorePatternsWithGitIgnore = ignorePatterns + parsedGitIgnorePatterns.map { Regex(it) }
        return ignorePatternsWithGitIgnore.any { p -> p.containsMatchIn(file.path) }
    }

    private fun parseGitIgnore(): List<String> {
        var gitIgnoreFile = File(projectRootPath, ".gitignore")
        if (!gitIgnoreFile.exists()) {
            return emptyList()
        }
        return gitIgnoreFile.readLines()
            .filterNot { it.isBlank() || it.startsWith("#") }
            .map { it.trim() }
            .map { convertGitIgnorePatternToRegex(it) }
    }

    private fun convertGitIgnorePatternToRegex(pattern: String): String = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .let { if (it.endsWith("/")) "$it?" else it } // Handle directory-specific patterns by optionally matching trailing slash

    companion object {
        private var instance: GitIgnoreParsingUtil? = null

        fun getInstance(projectRoot: String): GitIgnoreParsingUtil {
            if (instance == null) {
                instance = GitIgnoreParsingUtil(projectRoot)
            }
            return instance!!
        }
    }
}
