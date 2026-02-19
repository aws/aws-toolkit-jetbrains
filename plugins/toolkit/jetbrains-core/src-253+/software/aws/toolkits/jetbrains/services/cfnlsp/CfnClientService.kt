// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentItem
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateStackActionResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeValidationStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackActionStatusResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStateResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceTypesResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
internal class CfnClientService(project: Project) {
    private val lspServerProvider: () -> LspServer? = {
        LspServerManager.getInstance(project)
            .getServersForProvider(CfnLspServerSupportProvider::class.java)
            .firstOrNull()
    }

    fun updateIamCredentials(params: UpdateCredentialsParams): CompletableFuture<UpdateCredentialsResult?> =
        sendRequest { it.updateIamCredentials(params) }

    fun listStacks(params: ListStacksParams): CompletableFuture<ListStacksResult?> =
        sendRequest { it.listStacks(params) }

    fun listChangeSets(params: ListChangeSetsParams): CompletableFuture<ListChangeSetsResult?> =
        sendRequest { it.listChangeSets(params) }

    fun createValidation(params: CreateValidationParams): CompletableFuture<CreateStackActionResult?> =
        sendRequest { it.createValidation(params) }

    fun getValidationStatus(params: Identifiable): CompletableFuture<GetStackActionStatusResult?> =
        sendRequest { it.getValidationStatus(params) }

    fun describeValidationStatus(params: Identifiable): CompletableFuture<DescribeValidationStatusResult?> =
        sendRequest { it.describeValidationStatus(params) }

    fun listResourceTypes(): CompletableFuture<ResourceTypesResult?> =
        sendRequest { it.listResourceTypes() }

    fun removeResourceType(resourceType: String): CompletableFuture<Void?> =
        sendRequest { it.removeResourceType(resourceType) }

    fun searchResource(params: SearchResourceParams): CompletableFuture<SearchResourceResult?> =
        sendRequest { it.searchResource(params) }

    fun listResources(params: ListResourcesParams): CompletableFuture<ListResourcesResult?> =
        sendRequest { it.listResources(params) }

    fun refreshResources(params: RefreshResourcesParams): CompletableFuture<RefreshResourcesResult?> =
        sendRequest { it.refreshResources(params) }

    fun getStackManagementInfo(resourceIdentifier: String): CompletableFuture<ResourceStackManagementResult?> =
        sendRequest { it.getStackManagementInfo(resourceIdentifier) }

    fun getResourceState(params: ResourceStateParams): CompletableFuture<ResourceStateResult?> =
        sendRequest { it.getResourceState(params) }

    fun ensureDocumentOpen(file: VirtualFile, project: Project) {
        val isOpenInEditor = FileEditorManager.getInstance(project).isFileOpen(file)
        if (isOpenInEditor) return

        val server = lspServerProvider() ?: return
        val descriptor = server.descriptor
        val uri = descriptor.getFileUri(file)
        val languageId = descriptor.getLanguageId(file)
        val content = FileDocumentManager.getInstance().getDocument(file)?.text ?: return

        server.sendNotification { lsp ->
            lsp.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, languageId, 0, content))
            )
        }
    }
    fun describeStack(params: DescribeStackParams): CompletableFuture<DescribeStackResult?> =
        sendRequest { it.describeStack(params) }

    fun notifyConfigurationChanged() {
        lspServerProvider()?.sendNotification { lsp ->
            lsp.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>()))
        }
    }

    /**
     * Sends a request to the LSP server and returns a future with the result.
     * Uses sendNotification to safely access the server instance, then calls the
     * protocol method which returns its own CompletableFuture.
     */
    private fun <T> sendRequest(request: (CfnLspServerProtocol) -> CompletableFuture<T>): CompletableFuture<T?> {
        val future = CompletableFuture<T?>()
        val server = lspServerProvider()
        if (server == null) {
            future.complete(null)
            return future
        }
        server.sendNotification { lsp ->
            (lsp as? CfnLspServerProtocol)?.let { cfn ->
                request(cfn).whenComplete { result, error ->
                    if (error != null) {
                        future.completeExceptionally(error)
                    } else {
                        future.complete(result)
                    }
                }
            } ?: future.complete(null)
        }
        return future
    }

    companion object {
        fun getInstance(project: Project): CfnClientService = project.service()
    }
}
