// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.NodeRuntimeResolver
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnCredentialsService
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspServer
import software.aws.toolkits.jetbrains.settings.CfnLspSettings
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.nio.file.Path

private val SUPPORTED_EXTENSIONS = setOf("yaml", "yml", "json", "template", "cfn")

private fun VirtualFile.isCfnTemplate(): Boolean =
    extension?.lowercase() in SUPPORTED_EXTENSIONS

class CfnLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter,
    ) {
        if (!CfnLspSettings.getInstance().isLspEnabled) return
        if (file.isCfnTemplate()) {
            serverStarter.ensureServerStarted(CfnLspServerDescriptor(project))
        }
    }
}

private class CfnLspServerDescriptor(project: Project) :
    ProjectWideLspServerDescriptor(project, "AWS CloudFormation") {

    private val installer = CfnLspInstaller()

    override val lsp4jServerClass: Class<out LanguageServer> = CfnLspServer::class.java

    override fun isSupportedFile(file: VirtualFile) = file.isCfnTemplate()

    override fun createCommandLine(): GeneralCommandLine {
        val serverPath = try {
            installer.getServerPath()
        } catch (e: CfnLspException) {
            LOG.warn(e) { "Failed to get CloudFormation LSP server" }
            notifyLspError(e)
            throw e
        }

        val nodePath = try {
            resolveNodeRuntime()
        } catch (e: CfnLspException) {
            LOG.warn(e) { "Failed to resolve Node.js runtime" }
            notifyNodeError()
            throw e
        }

        LOG.info { "Starting CloudFormation LSP: node=$nodePath, server=$serverPath" }

        return GeneralCommandLine(nodePath.toString(), serverPath.toString(), "--stdio")
            .withWorkDirectory(serverPath.parent.toString())
    }

    private fun resolveNodeRuntime(): Path {
        val settings = CfnLspSettings.getInstance()

        if (settings.nodeRuntimePath.isNotBlank()) {
            return Path.of(settings.nodeRuntimePath)
        }

        return NodeRuntimeResolver.resolve()
            ?: throw CfnLspException(
                message("cloudformation.lsp.error.node_not_found"),
                CfnLspException.ErrorCode.NODE_NOT_FOUND
            )
    }

    private fun notifyLspError(e: CfnLspException) {
        val content = when (e.errorCode) {
            CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED -> message("cloudformation.lsp.error.manifest_failed")
            CfnLspException.ErrorCode.NO_COMPATIBLE_VERSION -> message("cloudformation.lsp.error.no_compatible_version")
            CfnLspException.ErrorCode.DOWNLOAD_FAILED -> message("cloudformation.lsp.error.download_failed")
            CfnLspException.ErrorCode.EXTRACTION_FAILED -> message("cloudformation.lsp.error.extraction_failed")
            CfnLspException.ErrorCode.NODE_NOT_FOUND -> message("cloudformation.lsp.error.node_not_found")
        }

        notifyError(
            title = message("cloudformation.lsp.error.title"),
            content = content,
            project = project
        )
    }

    private fun notifyNodeError() {
        notifyError(
            title = message("cloudformation.lsp.error.title"),
            content = message("cloudformation.lsp.error.node_not_found"),
            project = project,
            notificationActions = listOf(
                NotificationAction.createSimple(message("cloudformation.lsp.action.configure_node")) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, message("aws.settings.title"))
                }
            )
        )
    }

    override fun createInitializationOptions(): Any {
        val settings = CfnLspSettings.getInstance()
        val credentialsService = CfnCredentialsService.getInstance(project)

        return mapOf(
            "handledSchemaProtocols" to listOf("file"),
            "aws" to mapOf(
                "clientInfo" to mapOf(
                    "extension" to mapOf(
                        "name" to "aws-toolkit-jetbrains",
                        "version" to "1.0.0"
                    )
                ),
                "telemetryEnabled" to settings.isTelemetryEnabled,
                "encryption" to mapOf(
                    "key" to credentialsService.encryptionKeyBase64,
                    "algorithm" to "A256GCM"
                )
            )
        )
    }

    override fun getWorkspaceConfiguration(item: org.eclipse.lsp4j.ConfigurationItem): Any? {
        val section = item.section ?: return null
        val settings = CfnLspSettings.getInstance()

        return when (section) {
            "aws.cloudformation" -> buildCfnConfiguration(settings)
            "editor" -> buildEditorConfiguration()
            else -> null
        }
    }

    private fun buildCfnConfiguration(settings: CfnLspSettings): Map<String, Any?> = mapOf(
        "hover" to mapOf("enabled" to settings.isHoverEnabled),
        "completion" to mapOf(
            "enabled" to settings.isCompletionEnabled,
            "maxCompletions" to settings.maxCompletions
        ),
        "diagnostics" to mapOf(
            "cfnLint" to buildCfnLintConfiguration(settings),
            "cfnGuard" to buildCfnGuardConfiguration(settings)
        )
    )

    private fun buildCfnLintConfiguration(settings: CfnLspSettings): Map<String, Any?> = mapOf(
        "enabled" to settings.isCfnLintEnabled,
        "lintOnChange" to settings.cfnLintLintOnChange,
        "delayMs" to settings.cfnLintDelayMs,
        "includeExperimental" to settings.cfnLintIncludeExperimental,
        "ignoreChecks" to settings.cfnLintIgnoreChecks.toStringList(),
        "includeChecks" to settings.cfnLintIncludeChecks.toStringList(),
        "customRules" to settings.cfnLintCustomRules.toStringList(),
        "appendRules" to settings.cfnLintAppendRules.toStringList(),
        "overrideSpec" to settings.cfnLintOverrideSpec.ifEmpty { null },
        "registrySchemas" to settings.cfnLintRegistrySchemas.toStringList()
    )

    private fun buildCfnGuardConfiguration(settings: CfnLspSettings): Map<String, Any?> = mapOf(
        "enabled" to settings.isCfnGuardEnabled,
        "validateOnChange" to settings.cfnGuardValidateOnChange,
        "enabledRulePacks" to settings.cfnGuardEnabledRulePacks.toStringList(),
        "rulesFile" to settings.cfnGuardRulesFile.ifEmpty { null }
    )

    private fun buildEditorConfiguration(): Map<String, Any> = mapOf(
        "tabSize" to 2,
        "insertSpaces" to true,
        "detectIndentation" to true
    )

    private fun String.toStringList(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private val LOG = getLogger<CfnLspServerDescriptor>()
    }
}
