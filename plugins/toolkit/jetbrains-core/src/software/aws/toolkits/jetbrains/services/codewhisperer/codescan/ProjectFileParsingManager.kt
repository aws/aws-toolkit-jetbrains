// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.ignorePatterns
import java.io.File

class ProjectFileParsingManager(project: Project) {

    private var projectRoot = project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")
    private var parsedGitIgnorePatterns = emptyList<String>()
    private var gitIgnoreFile = File(projectRoot.path, ".gitignore")

    init {
        parsedGitIgnorePatterns = parseGitIgnore()
    }

    private fun convertGitIgnorePatternToRegex(pattern: String): String = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .let { if (it.endsWith("/")) "$it?" else it }

    private fun parseGitIgnore(): List<String> {
        if (!gitIgnoreFile.exists()) {
            return emptyList()
        }
        return gitIgnoreFile.readLines()
            .filterNot { it.isBlank() || it.startsWith("#") }
            .map { it.trim() }
            .map { convertGitIgnorePatternToRegex(it) }
    }

    fun ignoreFile(file: VirtualFile): Boolean {
        val ignorePatternsWithGitIgnore = ignorePatterns + parsedGitIgnorePatterns.map { Regex(it) }
        return ignorePatternsWithGitIgnore.any { p -> p.containsMatchIn(file.path) }
    }

    fun collectProjectFiles(selectedFile: VirtualFile): List<VirtualFile> {
        val files = mutableListOf(selectedFile)
        files.addAll(
            VfsUtil.collectChildrenRecursively(projectRoot).filter {
                !it.isDirectory && !ignoreFile(it) && it != selectedFile
            }
        )
        return files
    }

    fun getProjectSize(): Long {
        var projectSize = 0L
        VfsUtil.collectChildrenRecursively(projectRoot).filter {
            !it.isDirectory && !it.`is`((VFileProperty.SYMLINK)) && (
                !ignoreFile(it)
                )
        }.fold(0L) { acc, next ->
            projectSize = acc + next.length
            projectSize
        }
        return projectSize
    }

    companion object {
        fun getInstance(project: Project): ProjectFileParsingManager = ProjectFileParsingManager(project)
    }
}
