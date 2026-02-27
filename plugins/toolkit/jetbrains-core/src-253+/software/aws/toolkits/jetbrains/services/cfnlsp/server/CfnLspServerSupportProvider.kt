// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.lsp.NodeRuntimeResolver
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnCredentialsService
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspExtensionConfig
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspServerProtocol
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.CfnLspSettings
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.nio.file.Path

internal val CFN_SUPPORTED_EXTENSIONS = setOf("yaml", "yml", "json", "template", "cfn", "txt")

private fun VirtualFile.isCfnTemplate(): Boolean =
    extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS

internal class CfnLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter,
    ) {
        if (file.isCfnTemplate()) {
            serverStarter.ensureServerStarted(CfnLspServerDescriptor.getInstance(project))
        }
    }
}

class CfnLspServerDescriptor private constructor(project: Project) :
    ProjectWideLspServerDescriptor(project, "AWS CloudFormation") {

    private val installer = CfnLspInstaller()

    override val lsp4jServerClass: Class<out LanguageServer> = CfnLspServerProtocol::class.java

    override fun isSupportedFile(file: VirtualFile) = file.isCfnTemplate()

    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
        Lsp4jClient(CfnLspNotificationsHandler(handler))

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
            CfnLspException.ErrorCode.HASH_VERIFICATION_FAILED -> message("cloudformation.lsp.error.hash_mismatch")
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
                        "name" to CfnLspExtensionConfig.EXTENSION_NAME,
                        "version" to CfnLspExtensionConfig.EXTENSION_VERSION
                    ),
                    "clientId" to AwsSettings.getInstance().clientId.toString()
                ),
                "telemetryEnabled" to settings.isTelemetryEnabled,
                "encryption" to mapOf(
                    "key" to credentialsService.encryptionKeyBase64,
                    "mode" to CfnLspExtensionConfig.ENCRYPTION_MODE
                )
            )
        )
    }

    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? {
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

    private fun buildEditorConfiguration(): Map<String, Any> {
        val indentOptions = com.intellij.psi.codeStyle.CodeStyleSettings.getDefaults().indentOptions
        return mapOf(
            "tabSize" to indentOptions.TAB_SIZE,
            "insertSpaces" to !indentOptions.USE_TAB_CHARACTER,
            "detectIndentation" to true
        )
    }

    private fun String.toStringList(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private val LOG = getLogger<CfnLspServerDescriptor>()
        private val instances = mutableMapOf<Project, CfnLspServerDescriptor>()

        fun getInstance(project: Project): CfnLspServerDescriptor =
            instances.getOrPut(project) { CfnLspServerDescriptor(project) }
    }
}

private class CfnLspNotificationsHandler(
    private val delegate: LspServerNotificationsHandler,
) : LspServerNotificationsHandler by delegate {
    override fun logMessage(params: MessageParams) {
        LOG.info { "CloudFormation language server [${params.type}]: ${params.message}" }
    }

    companion object {
        private val LOG = getLogger<CfnLspNotificationsHandler>()
    }
}
