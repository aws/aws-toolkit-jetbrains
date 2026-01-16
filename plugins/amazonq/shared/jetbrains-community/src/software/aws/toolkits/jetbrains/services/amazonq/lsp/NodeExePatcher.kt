// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import com.intellij.util.text.nullize
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.exists
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.LspSettings
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Telemetry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Hacky nonsense to support old glibc platforms like AL2
 * @see "https://github.com/microsoft/vscode/issues/231623"
 * @see "https://github.com/aws/aws-toolkit-vscode/commit/6081f890bdbb91fcd8b60c4cc0abb65b15d4a38d"
 */
object NodeExePatcher {
    const val GLIBC_LINKER_VAR = "VSCODE_SERVER_CUSTOM_GLIBC_LINKER"
    const val GLIBC_PATH_VAR = "VSCODE_SERVER_CUSTOM_GLIBC_PATH"
    const val INTERNAL_AARCH64_LINKER = "/opt/vsc-sysroot/lib/ld-linux-aarch64.so.1"
    const val INTERNAL_X86_64_LINKER = "/opt/vsc-sysroot/lib/ld-linux-x86-64.so.2"
    const val INTERNAL_GLIBC_PATH = "/opt/vsc-sysroot/lib/"

    fun patch(node: Path): GeneralCommandLine {
        val nodePath = node.toAbsolutePath().toString()

        return if (!linker.isNullOrEmpty() && glibc.isNotEmpty() && Paths.get(linker).exists() && Paths.get(glibc).exists()) {
            GeneralCommandLine(linker)
                .withParameters("--library-path", glibc, nodePath)
                .also {
                    getLogger<NodeExePatcher>().info { "Using glibc patch: $it" }
                }
        } else {
            GeneralCommandLine(nodePath)
        }
    }

    /**
     * Resolves the path to a valid Node.js runtime in the following order of preference:
     * 1. Uses the provided nodePath if it exists and is executable
     * 2. Uses user-specified runtime path from LSP settings if available
     * 3. Uses system Node.js if version 18+ is available
     * 4. Falls back to original nodePath with a notification to configure runtime
     *
     * @param nodePath The initial Node.js runtime path to check, typically from the artifact directory
     * @return Path The resolved Node.js runtime path to use for the LSP server
     *
     * Side effects:
     * - Logs warnings if initial runtime path is invalid
     * - Logs info when using alternative runtime path
     * - Shows notification to user if no valid Node.js runtime is found
     *
     * Note: The function will return a path even if no valid runtime is found, but the LSP server
     * may fail to start in that case. The caller should handle potential runtime initialization failures.
     */
    fun getNodeRuntimePath(project: Project, nodePath: Path): Path {
        val resolveNodeMetric = { isBundled: Boolean, success: Boolean ->
            Telemetry.languageserver.setup.use {
                it.id("q")
                    .metadata("languageServerSetupStage", "resolveNode")
                    .metadata("credentialStartUrl", getStartUrl(project))
                    .metadata("isBundledNode", isBundled.toString())
                    .success(success)
            }
        }

        // attempt to use user provided node runtime path
        val nodeRuntime = LspSettings.getInstance().getNodeRuntimePath()
        if (!nodeRuntime.isNullOrEmpty()) {
            LOG.info { "Using node from $nodeRuntime " }

            resolveNodeMetric(false, true)
            return Path.of(nodeRuntime)
        }

        // attempt to use bundled node
        if (Files.exists(nodePath) && validateNode(nodePath) != null) {
            resolveNodeMetric(true, true)
            return nodePath
        } else {
            // use alternative node runtime if it is not found
            LOG.warn { "Node Runtime download failed. Fallback to user environment search" }

            val localNode = locateNodeCommand()
            if (localNode != null) {
                LOG.info { "Using node from ${localNode.toAbsolutePath()}" }

                resolveNodeMetric(false, true)
                return localNode
            }
            notifyInfo(
                "Amazon Q",
                message("amazonqFeatureDev.placeholder.node_runtime_message"),
                project = project,
                listOf(
                    NotificationAction.create(
                        message("codewhisperer.actions.open_settings.title")
                    ) { _, notification ->
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, message("aws.settings.codewhisperer.configurable.title"))
                    },
                    NotificationAction.create(
                        message("codewhisperer.notification.custom.simple.button.got_it")
                    ) { _, notification -> notification.expire() }
                )
            )

            resolveNodeMetric(false, false)
            return nodePath
        }
    }

    /**
     * Locates node executable ≥18 in system PATH.
     * Uses IntelliJ's PathEnvironmentVariableUtil to find executables.
     *
     * @return Path? The absolute path to node ≥18 if found, null otherwise
     */
    private fun locateNodeCommand(): Path? {
        val exeName = if (SystemInfo.isWindows) "node.exe" else "node"

        return PathEnvironmentVariableUtil.findAllExeFilesInPath(exeName)
            .asSequence()
            .map { it.toPath() }
            .filter { Files.isRegularFile(it) && Files.isExecutable(it) }
            .firstNotNullOfOrNull(::validateNode)
    }

    /** @return null if node is not suitable **/
    private fun validateNode(path: Path) = try {
        val process = patch(path)
            .withParameters("--version")
            .withRedirectErrorStream(true)
        val output = ExecUtil.execAndGetOutput(
            process,
            5000
        )

        LOG.debug { "$process: ${output.stdout.trim()}" }

        if (output.exitCode == 0) {
            val version = output.stdout.trim()
            val majorVersion = version.removePrefix("v").split(".")[0].toIntOrNull()

            if (majorVersion != null && majorVersion >= 18) {
                LOG.debug { "Node $version found at: $path" }
                path.toAbsolutePath()
            } else {
                LOG.debug { "Node version < 18 found at: $path (version: $version)" }
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

    private val linker
        get() = System.getenv(GLIBC_LINKER_VAR).nullize(true) ?: let {
            if (CpuArch.isArm64()) {
                INTERNAL_AARCH64_LINKER
            } else if (CpuArch.isIntel64()) {
                INTERNAL_X86_64_LINKER
            } else {
                null
            }
        }

    private val glibc
        get() = System.getenv(GLIBC_PATH_VAR).nullize(true) ?: INTERNAL_GLIBC_PATH

    private val LOG = getLogger<NodeExePatcher>()
}
