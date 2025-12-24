// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import java.io.File

object AmazonQCodeReviewGitUtils {
    private val LOG = getLogger<AmazonQCodeReviewGitUtils>()
    private const val PROCESS_TIMEOUT_MS = 5000L

    /**
     * Executes a git command and returns the process output
     */
    private fun executeGitCommand(
        workDir: File,
        vararg parameters: String,
        timeoutMs: Long = PROCESS_TIMEOUT_MS,
    ): Pair<String, String> {
        val commandLine = GeneralCommandLine().apply {
            workDirectory = workDir
            exePath = "git"
            addParameters(*parameters)
        }

        return try {
            val output = ExecUtil.execAndGetOutput(commandLine, timeoutMs.toInt())
            if (output.exitCode != 0) {
                LOG.debug { "Git command failed with exit code ${output.exitCode}: ${output.stderr}" }
            }
            Pair(output.stdout.trim(), output.stderr.trim())
        } catch (e: Exception) {
            LOG.debug(e) { "Git command failed: ${commandLine.commandLineString}" }
            Pair("", e.message ?: "Unknown error")
        }
    }

    fun runGitDiffHead(
        projectName: String,
        root: VirtualFile,
        relativeFilePath: String? = null,
        newFile: Boolean? = false,
    ): String {
        if (!root.exists()) {
            LOG.debug { "Root directory does not exist: ${root.path}" }
            return ""
        }

        val prefixes = arrayOf(
            "--src-prefix=a/$projectName/",
            "--dst-prefix=b/$projectName/"
        )
        val ref = if (SystemInfo.isWindows) "NUL" else "/dev/null"

        val parameters = when {
            relativeFilePath == null -> arrayOf("diff", "HEAD") + prefixes
            newFile == true -> arrayOf("diff", "--no-index", *prefixes, ref, relativeFilePath)
            else -> arrayOf("diff", "HEAD", *prefixes, relativeFilePath)
        }

        val (output, error) = executeGitCommand(File(root.path), *parameters)

        return when {
            error.contains("Authentication failed") -> {
                LOG.debug { "Git Authentication Failed" }
                throw RuntimeException("Git Authentication Failed")
            }
            error.isNotEmpty() -> {
                LOG.debug { "Git command failed: $error" }
                ""
            }
            else -> output
        }
    }

    fun isGitRoot(file: VirtualFile): Boolean {
        if (!file.exists()) return false

        val workDir = if (file.isDirectory) File(file.path) else File(file.parent.path)
        val (output, _) = executeGitCommand(workDir, "rev-parse", "--git-dir")

        return output == ".git"
    }

    fun getGitRepositoryRoot(file: VirtualFile): VirtualFile? {
        if (!file.exists()) return null

        val workDir = if (file.isDirectory) {
            File(file.path)
        } else {
            File(file.parent.path)
        }

        if (!workDir.exists() || !workDir.isDirectory) {
            LOG.debug { "Invalid working directory: ${workDir.path}" }
            return null
        }

        val (output, error) = executeGitCommand(workDir, "rev-parse", "--show-toplevel")

        return when {
            error.isNotEmpty() -> {
                LOG.debug { "Failed to get git root: $error" }
                null
            }
            output.isEmpty() -> null
            else -> LocalFileSystem.getInstance().findFileByPath(output)
        }
    }

    fun getUnstagedFiles(root: VirtualFile): List<String> {
        if (!root.exists()) return emptyList()

        val (output, error) = executeGitCommand(
            File(root.path),
            "ls-files",
            "--others",
            "--exclude-standard"
        )

        return when {
            error.isNotEmpty() -> {
                LOG.debug { "Failed to get unstaged files: $error" }
                emptyList()
            }
            else -> output.split("\n").filter { it.isNotEmpty() }
        }
    }

    fun isInsideWorkTree(folder: VirtualFile): Boolean {
        val (output) = executeGitCommand(File(folder.path), "rev-parse", "--is-inside-work-tree")
        return output == "true"
    }
}
