// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cloudformation.settings.CfnSettings
import java.io.File
import java.nio.file.Paths

class CfnLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter,
    ) {
        if (!CfnSettings.getInstance().isLspEnabled) return
        if (isSupportedFile(file)) {
            serverStarter.ensureServerStarted(CfnLspServerDescriptor.getInstance(project))
        }
    }

    private fun isSupportedFile(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = listOf("yaml", "yml", "json", "template", "cfn")
    }
}

private class CfnLspServerDescriptor private constructor(project: Project) :
    ProjectWideLspServerDescriptor(project, "AWS CloudFormation") {

    private val installer = CfnLspInstaller(getStorageDir())

    override fun isSupportedFile(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    override fun createCommandLine(): GeneralCommandLine {
        val serverPath = installer.getServerPath()
        val nodePath = findNodeExecutable()

        LOG.info("Starting CloudFormation LSP: node=$nodePath, server=$serverPath")

        return GeneralCommandLine(nodePath, serverPath.toString(), "--stdio")
            .withWorkDirectory(serverPath.parent.toString())
    }

    override fun createInitializationOptions(): Any {
        val settings = CfnSettings.getInstance()
        return mapOf(
            "handledSchemaProtocols" to listOf("file"),
            "aws" to mapOf(
                "clientInfo" to mapOf(
                    "extension" to mapOf(
                        "name" to "aws-toolkit-jetbrains",
                        "version" to "1.0.0"
                    )
                ),
                "telemetryEnabled" to settings.isTelemetryEnabled
            )
        )
    }

    override fun getWorkspaceConfiguration(item: org.eclipse.lsp4j.ConfigurationItem): Any? {
        val section = item.section ?: return null
        val settings = CfnSettings.getInstance()

        return when (section) {
            "aws.cloudformation" -> buildCfnConfiguration(settings)
            "editor" -> buildEditorConfiguration()
            else -> null
        }
    }

    private fun buildCfnConfiguration(settings: CfnSettings): Map<String, Any?> = mapOf(
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

    private fun buildCfnLintConfiguration(settings: CfnSettings): Map<String, Any?> = mapOf(
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

    private fun buildCfnGuardConfiguration(settings: CfnSettings): Map<String, Any?> = mapOf(
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

    private fun findNodeExecutable(): String {
        val path = System.getenv("PATH") ?: throw IllegalStateException("PATH not set")

        for (dir in path.split(File.pathSeparator)) {
            val node = Paths.get(dir, "node").toFile()
            if (node.canExecute()) return node.absolutePath

            val nodeExe = Paths.get(dir, "node.exe").toFile()
            if (nodeExe.canExecute()) return nodeExe.absolutePath
        }

        throw IllegalStateException("Node.js not found in PATH")
    }

    private fun getStorageDir() = Paths.get(
        System.getProperty("user.home"),
        ".aws-toolkit-jetbrains",
        "cloudformation-lsp"
    )

    companion object {
        private val LOG = logger<CfnLspServerDescriptor>()
        private val SUPPORTED_EXTENSIONS = listOf("yaml", "yml", "json", "template", "cfn")
        private val instances = mutableMapOf<Project, CfnLspServerDescriptor>()

        fun getInstance(project: Project): CfnLspServerDescriptor =
            instances.getOrPut(project) { CfnLspServerDescriptor(project) }
    }
}
