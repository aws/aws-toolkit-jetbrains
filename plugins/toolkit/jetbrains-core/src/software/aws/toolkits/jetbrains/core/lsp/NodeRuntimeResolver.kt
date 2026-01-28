// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.getLogger
import java.nio.file.Files
import java.nio.file.Path

object NodeRuntimeResolver {
    private val LOG = getLogger<NodeRuntimeResolver>()

    /**
     * Locates a Node.js executable with version >= minVersion.
     * Uses IntelliJ's PathEnvironmentVariableUtil to search PATH.
     *
     * @return Path to valid Node.js executable, or null if not found
     */
    fun resolve(minVersion: Int = 18): Path? {
        val exeName = if (SystemInfo.isWindows) "node.exe" else "node"

        return PathEnvironmentVariableUtil.findAllExeFilesInPath(exeName)
            .asSequence()
            .map { it.toPath() }
            .filter { Files.isRegularFile(it) && Files.isExecutable(it) }
            .firstNotNullOfOrNull { validateVersion(it, minVersion) }
    }

    private fun validateVersion(path: Path, minVersion: Int): Path? = try {
        val output = ExecUtil.execAndGetOutput(
            com.intellij.execution.configurations.GeneralCommandLine(path.toString(), "--version"),
            5000
        )

        if (output.exitCode == 0) {
            val version = output.stdout.trim()
            val majorVersion = version.removePrefix("v").split(".")[0].toIntOrNull()

            if (majorVersion != null && majorVersion >= minVersion) {
                LOG.debug { "Node $version found at: $path" }
                path.toAbsolutePath()
            } else {
                LOG.debug { "Node version < $minVersion at: $path (version: $version)" }
                null
            }
        } else {
            LOG.debug { "Failed to get version from node at: $path" }
            null
        }
    } catch (e: Exception) {
        LOG.debug(e) { "Failed to check version for node at: $path" }
        null
    }
}
