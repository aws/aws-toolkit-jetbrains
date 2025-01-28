// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class GitIgnoreFilteringUtil(private val moduleDir: VirtualFile) {
    private var ignorePatternsWithGitIgnore = emptyList<Regex>()
    private val additionalGitIgnoreRules = setOf(
        ".aws-sam",
        ".gem",
        ".git",
        ".gitignore",
        ".gradle",
        ".hg",
        ".idea",
        ".project",
        ".rvm",
        ".svn",
        "*.zip",
        "*.bin",
        "*.png",
        "*.jpg",
        "*.svg",
        "*.pyc",
        "license.txt",
        "License.txt",
        "LICENSE.txt",
        "license.md",
        "License.md",
        "LICENSE.md",
        "node_modules",
        "build",
        "dist",
        "annotation-generated-src",
        "annotation-generated-tst"
    )

    init {
        ignorePatternsWithGitIgnore = try {
            buildList {
                addAll(additionalGitIgnoreRules.map { convertGitIgnorePatternToRegex(it) })
                addAll(parseGitIgnore())
            }.mapNotNull { pattern ->
                runCatching { Regex(pattern) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseGitIgnore(): Set<String> {
        val gitignoreFile = moduleDir.findChild(".gitignore")
        return gitignoreFile?.let {
            if (it.isValid && it.exists()) {
                it.inputStream.bufferedReader().readLines()
                    .filter { line ->
                        line.isNotBlank() && !line.startsWith("#")
                    }
                    .map { pattern ->
                        convertGitIgnorePatternToRegex(pattern.trim())
                    }
                    .toSet()
            } else {
                emptySet()
            }
        } ?: emptySet()
    }

    // gitignore patterns are not regex, method update needed.
    private fun convertGitIgnorePatternToRegex(pattern: String): String = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .let { if (it.endsWith("/")) "$it.*" else "$it/.*" } // Add a trailing /* to all patterns. (we add a trailing / to all files when matching)

    suspend fun ignoreFile(file: VirtualFile): Boolean {
        // this method reads like something a JS dev would write and doesn't do what the author thinks
        val deferredResults = ignorePatternsWithGitIgnore.map { pattern ->
            withContext(coroutineContext) {
                // avoid partial match (pattern.containsMatchIn) since it causes us matching files
                // against folder patterns. (e.g. settings.gradle ignored by .gradle rule!)
                // we convert the glob rules to regex, add a trailing /* to all rules and then match
                // entries against them by adding a trailing /.
                // TODO: Add unit tests for gitignore matching
                val relative = getRelativePath(file)
                async { pattern.matches("$relative/") }
            }
        }

        // this will serially iterate over and block
        // ideally we race the results https://github.com/Kotlin/kotlinx.coroutines/issues/2867
        // i.e. Promise.any(...)
        return deferredResults.any { it.await() }
    }

    private fun getRelativePath(file: VirtualFile): String = VfsUtil.getRelativePath(file, moduleDir) ?: ""
}
