// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.GetConfigurationFromServerParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.SyncModuleDependenciesParams
import java.util.concurrent.CompletableFuture

/**
 * Remote interface exposed by the Amazon Q language server
 */
@Suppress("unused")
interface AmazonQLanguageServer : LanguageServer {
    @JsonNotification("aws/syncModuleDependencies")
    fun syncModuleDependencies(params: SyncModuleDependenciesParams): CompletableFuture<Unit>

    @JsonRequest("aws/credentials/token/update")
    fun updateTokenCredentials(payload: UpdateCredentialsPayload): CompletableFuture<ResponseMessage>

    @JsonNotification("aws/credentials/token/delete")
    fun deleteTokenCredentials(): CompletableFuture<Unit>

    @JsonRequest("aws/getConfigurationFromServer")
    fun getConfigurationFromServer(params: GetConfigurationFromServerParams): CompletableFuture<LspServerConfigurations>
}
