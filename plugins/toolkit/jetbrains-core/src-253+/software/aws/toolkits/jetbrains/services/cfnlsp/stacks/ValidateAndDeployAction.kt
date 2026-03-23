// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VfsUtil
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.documents.CfnDocumentManager
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StackNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Parameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Tag
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateParameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateResource
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ValidateAndDeployWizard
import software.aws.toolkits.jetbrains.utils.notifyError
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class ValidateAndDeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val clientService = CfnClientService.getInstance(project)

        val selectedNode = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)?.firstOrNull()
        val templateFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedEditor?.file?.takeIf {
                it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
            }

        val prefilledTemplate = templateFile?.path
        val prefilledStackName = (selectedNode as? StackNode)?.stack?.stackName

        val documentManager = CfnDocumentManager.getInstance(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading template configuration...", true) {
            private var templateParams: List<TemplateParameter> = emptyList()
            private var detectedCaps: List<String> = emptyList()
            private var hasArtifacts = false
            private var artifactError: String? = null
            private var existingParams: List<Parameter>? = null
            private var existingTags: List<Tag>? = null
            private var templateResources: List<TemplateResource> = emptyList()
            private var isExistingStack = false

            override fun run(indicator: ProgressIndicator) {
                if (templateFile != null) {
                    val descriptor = CfnLspServerDescriptor.getInstance(project)
                    val uri = descriptor.getFileUri(templateFile)
                    clientService.ensureDocumentOpen(templateFile, project)

                    indicator.text = "Fetching template parameters..."
                    try {
                        templateParams = clientService.getParameters(uri).get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.parameters.orEmpty()
                    } catch (ex: Exception) {
                        LOG.warn(ex) { "Failed to fetch template parameters" }
                    }

                    indicator.text = "Analyzing capabilities..."
                    try {
                        detectedCaps = clientService.getCapabilities(uri).get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.capabilities.orEmpty()
                    } catch (ex: Exception) {
                        LOG.warn(ex) { "Failed to fetch capabilities" }
                    }

                    indicator.text = "Checking artifacts..."
                    try {
                        val artifactsResult = clientService.getTemplateArtifacts(uri).get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        val artifacts = artifactsResult?.artifacts.orEmpty()
                        hasArtifacts = artifacts.isNotEmpty()
                        val templateDir = templateFile.parent?.path.orEmpty()
                        for (artifact in artifacts) {
                            val artifactPath = if (artifact.filePath.startsWith("/")) {
                                artifact.filePath
                            } else {
                                "$templateDir/${artifact.filePath}"
                            }
                            if (!File(artifactPath).exists()) {
                                artifactError = artifact.filePath
                                return
                            }
                        }
                    } catch (ex: Exception) {
                        LOG.warn(ex) { "Failed to check artifacts" }
                    }

                    indicator.text = "Fetching template resources..."
                    try {
                        templateResources = clientService.getTemplateResources(uri).get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.resources.orEmpty()
                    } catch (ex: Exception) {
                        LOG.warn(ex) { "Failed to fetch template resources" }
                    }
                }

                if (prefilledStackName != null) {
                    indicator.text = "Fetching stack details..."
                    try {
                        val stackResult = clientService.describeStack(DescribeStackParams(prefilledStackName)).get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        existingParams = stackResult?.stack?.parameters
                        existingTags = stackResult?.stack?.tags
                        isExistingStack = stackResult?.stack != null
                    } catch (ex: Exception) {
                        LOG.warn(ex) { "Failed to fetch stack details" }
                    }
                }
            }

            override fun onSuccess() {
                if (artifactError != null) {
                    notifyError("CloudFormation", "Artifact path does not exist: $artifactError", project = project)
                    return
                }

                val wizard = ValidateAndDeployWizard(
                    project = project,
                    documentManager = documentManager,
                    prefilledTemplatePath = prefilledTemplate,
                    prefilledStackName = prefilledStackName,
                    templateParameters = templateParams,
                    detectedCapabilities = detectedCaps,
                    existingParameters = existingParams,
                    existingTags = existingTags,
                    hasArtifacts = hasArtifacts,
                    templateResources = templateResources,
                    isExistingStack = isExistingStack,
                )

                if (!wizard.showAndGet()) return

                val settings = wizard.getSettings()
                val templateVFile = VfsUtil.findFileByIoFile(File(settings.templatePath), true) ?: return

                val desc = CfnLspServerDescriptor.getInstance(project)
                clientService.ensureDocumentOpen(templateVFile, project)

                val params = CreateValidationParams(
                    id = UUID.randomUUID().toString(),
                    uri = desc.getFileUri(templateVFile),
                    stackName = settings.stackName,
                    parameters = settings.parameters.ifEmpty { null },
                    capabilities = settings.capabilities.ifEmpty { null },
                    tags = settings.tags.ifEmpty { null },
                    resourcesToImport = settings.resourcesToImport,
                    keepChangeSet = true,
                    onStackFailure = settings.onStackFailure,
                    includeNestedStacks = settings.includeNestedStacks,
                    importExistingResources = settings.importExistingResources,
                    deploymentMode = settings.deploymentMode,
                    s3Bucket = settings.s3Bucket,
                    s3Key = settings.s3Key,
                )

                ValidationWorkflow(project).validate(params)
            }
        })
    }

    companion object {
        private val LOG = getLogger<ValidateAndDeployAction>()
        private const val LSP_TIMEOUT_SECONDS = 30L
    }
}
