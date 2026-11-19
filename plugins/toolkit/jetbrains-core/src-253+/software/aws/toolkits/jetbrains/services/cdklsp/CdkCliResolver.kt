// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cdklsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import software.aws.toolkit.core.utils.debug
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import java.nio.file.Files
import java.nio.file.Path

/** First aws-cdk release that ships `cdk lsp` (aws/aws-cdk-cli#1681, tag aws-cdk@v2.1132.0). */
internal val MINIMUM_CDK_LSP_VERSION = intArrayOf(2, 1132, 0)

/** The floor rendered for user-facing messages, derived from the single source of truth above. */
internal val MINIMUM_CDK_LSP_VERSION_STRING = MINIMUM_CDK_LSP_VERSION.joinToString(".")

/** Where the CDK CLI was found (mirrors the VS Code discovery ladder). */
internal enum class CdkCliSource { SETTING, NODE_MODULES, PATH }

internal data class ResolvedCdkCli(val path: Path, val source: CdkCliSource, val version: String)

/**
 * Resolves the user's `cdk` CLI for a given CDK app directory, in order:
 *   1. an explicit configured path (aws.cdk.cliPath),
 *   2. the app's local node_modules/.bin/cdk (walking up),
 *   3. `cdk` on PATH.
 * Only returns a CLI whose version is >= [MINIMUM_CDK_LSP_VERSION]; older or
 * missing CLIs return null so the caller can prompt to upgrade rather than
 * spawn a `cdk` that has no `lsp` command. Preferring the project-local CLI
 * keeps the language server aligned with the version the project synths with.
 */
internal object CdkCliResolver {
    private val LOG = getLogger<CdkCliResolver>()
    private val binName = if (SystemInfo.isWindows) "cdk.cmd" else "cdk"

    fun resolve(appDir: Path, configuredPath: String?): ResolvedCdkCli? {
        // 1. Setting override.
        configuredPath?.takeIf { it.isNotBlank() }?.let {
            val p = Path.of(it)
            versionOf(p)?.let { v -> return ResolvedCdkCli(p, CdkCliSource.SETTING, v) }
            LOG.warn { "aws.cdk.cliPath is set but unusable: $it" }
        }

        // 2. Workspace-local node_modules/.bin/cdk, walking up from the app dir.
        findInNodeModules(appDir)?.let { p ->
            versionOf(p)?.let { v -> return ResolvedCdkCli(p, CdkCliSource.NODE_MODULES, v) }
        }

        // 3. PATH (IDE resolves the login-shell PATH natively via this API).
        PathEnvironmentVariableUtil.findAllExeFilesInPath("cdk")
            .asSequence()
            .map { it.toPath() }
            .firstNotNullOfOrNull { p -> versionOf(p)?.let { v -> ResolvedCdkCli(p, CdkCliSource.PATH, v) } }
            ?.let { return it }

        return null
    }

    private fun findInNodeModules(appDir: Path): Path? {
        var dir: Path? = appDir
        while (dir != null) {
            val candidate = dir.resolve("node_modules").resolve(".bin").resolve(binName)
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate
            dir = dir.parent
        }
        return null
    }

    /** Returns the parsed version string if `<cdk> --version` reports >= the floor, else null. */
    private fun versionOf(cdk: Path): String? {
        if (!Files.isRegularFile(cdk) || !Files.isExecutable(cdk)) return null
        return try {
            val out = ExecUtil.execAndGetOutput(GeneralCommandLine(cdk.toString(), "--version"), 5000)
            if (out.exitCode != 0) return null
            val version = Regex("""(\d+)\.(\d+)\.(\d+)""").find(out.stdout)?.value ?: return null
            if (meetsMinimum(version)) version else null
        } catch (e: Exception) {
            LOG.debug(e) { "Failed to probe cdk version at $cdk" }
            null
        }
    }

    private fun meetsMinimum(version: String): Boolean {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val a = parts.getOrElse(i) { 0 }
            val b = MINIMUM_CDK_LSP_VERSION[i]
            if (a != b) return a > b
        }
        return true
    }
}
