// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cdklsp.server

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkits.jetbrains.services.cdklsp.CdkCliResolver
import software.aws.toolkits.jetbrains.services.cdklsp.MINIMUM_CDK_LSP_VERSION_STRING
import software.aws.toolkits.jetbrains.settings.CdkLspSettings
import java.nio.file.Path

// Source files the server links (ts/py/java) plus synthesized templates for
// template -> construct go-to-definition. Matches the VS Code documentSelector.
private val CDK_SOURCE_EXTENSIONS = setOf("ts", "py", "java")

private fun VirtualFile.isCdkRelevant(): Boolean =
    extension?.lowercase() in CDK_SOURCE_EXTENSIONS || name.endsWith(".template.json")

// CdkLspServerSupportProvider must not be moved/renamed if referenced by class name.
internal class CdkLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter,
    ) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        // Gate: only start where there's a CDK app and the file is source/template.
        if (file.isCdkRelevant() && findCdkAppDir(project) != null) {
            serverStarter.ensureServerStarted(CdkLspServerDescriptor.getInstance(project))
        }
    }
}

class CdkLspServerDescriptor private constructor(project: Project) :
    ProjectWideLspServerDescriptor(project, "AWS CDK") {

    // Resolved once per (re)start in createCommandLine and reused by
    // createInitializationOptions, so both agree and a settings-change restart
    // re-resolves. createCommandLine always runs before the init options.
    private var currentAppDir: Path? = null

    override val lsp4jServerClass: Class<out LanguageServer> = LanguageServer::class.java

    override fun isSupportedFile(file: VirtualFile) = file.isCdkRelevant()

    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient = CdkLspClient(handler)

    override fun createCommandLine(): GeneralCommandLine {
        val appDir = findCdkAppDir(project) ?: error("No CDK app (cdk.json) found in project")
        currentAppDir = appDir

        // Settings changes (cliPath/appDir) restart the server via CdkLspStartupActivity.
        val resolved = CdkCliResolver.resolve(appDir, configuredPath = CdkLspSettings.getInstance().cliPath)
        if (resolved == null) {
            notifyUpgrade(project)
            error("No AWS CDK CLI >= $MINIMUM_CDK_LSP_VERSION_STRING found for CDK language features")
        }

        LOG.info { "Starting `cdk lsp` for $appDir (cdk ${resolved.version} via ${resolved.source})" }
        // `cdk lsp` speaks LSP over stdio and logs to stderr; no transport flag needed.
        return GeneralCommandLine(resolved.path.toString(), "lsp").withWorkDirectory(appDir.toString())
    }

    // The server reads its app dir from this option (parity with the VS Code client).
    override fun createInitializationOptions(): Any = mapOf("applicationDir" to currentAppDir?.toString())

    private fun notifyUpgrade(project: Project) {
        com.intellij.notification.Notification(
            "aws.cdk.lsp",
            "AWS CDK",
            "CDK language features need the AWS CDK CLI >= $MINIMUM_CDK_LSP_VERSION_STRING. Upgrade the CLI or set aws.cdk.cliPath.",
            NotificationType.WARNING
        ).notify(project)
    }

    companion object {
        private val LOG = getLogger<CdkLspServerDescriptor>()
        private val instances = mutableMapOf<Project, CdkLspServerDescriptor>()

        fun getInstance(project: Project): CdkLspServerDescriptor =
            instances.getOrPut(project) { CdkLspServerDescriptor(project) }
    }
}

/**
 * Locate the CDK app directory (the folder containing cdk.json). The
 * aws.cdk.appDir setting takes precedence; otherwise the lowest-path cdk.json
 * wins so the pick is deterministic when a workspace has several apps.
 */
private fun findCdkAppDir(project: Project): Path? {
    // Explicit override wins.
    CdkLspSettings.getInstance().appDir.takeIf { it.isNotBlank() }?.let { return Path.of(it) }

    // FilenameIndex requires a read action and ready indexes. createCommandLine
    // runs on a pooled thread during startup (possibly dumb mode), so query in
    // smart mode under a read lock. Deterministic pick (lowest path); node_modules ignored.
    val cdkJson = DumbService.getInstance(project).runReadActionInSmartMode<VirtualFile?> {
        FilenameIndex.getVirtualFilesByName("cdk.json", GlobalSearchScope.projectScope(project))
            .filter { !it.path.contains("/node_modules/") }
            .minByOrNull { it.path }
    } ?: return null
    return cdkJson.parent?.toNioPath()
}
