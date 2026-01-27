// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import org.eclipse.lsp4j.DidChangeConfigurationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksResult
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

    fun listStacks(params: ListStacksParams): CompletableFuture<ListStacksResult?> =
        sendRequest { it.listStacks(params) }

    fun listChangeSets(params: ListChangeSetsParams): CompletableFuture<ListChangeSetsResult?> =
        sendRequest { it.listChangeSets(params) }

    fun updateIamCredentials(params: UpdateCredentialsParams): CompletableFuture<UpdateCredentialsResult?> =
        sendRequest { it.updateIamCredentials(params) }

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
