// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.GetConfigurationFromServerParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LogInlineCompletionSessionResultsParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionWithReferencesParams
import java.util.concurrent.CompletableFuture

/**
 * Remote interface exposed by the Amazon Q language server
 */
@Suppress("unused")
interface AmazonQLanguageServer : LanguageServer {
    @JsonRequest("aws/textDocument/inlineCompletionWithReferences")
    fun inlineCompletionWithReferences(params: InlineCompletionWithReferencesParams): CompletableFuture<InlineCompletionListWithReferences>

    @JsonNotification("aws/logInlineCompletionSessionResults")
    fun logInlineCompletionSessionResults(params: LogInlineCompletionSessionResultsParams): CompletableFuture<Unit>

    @JsonNotification("aws/didChangeDependencyPaths")
    fun didChangeDependencyPaths(params: DidChangeDependencyPathsParams): CompletableFuture<Unit>

    @JsonRequest("aws/credentials/token/update")
    fun updateTokenCredentials(payload: UpdateCredentialsPayload): CompletableFuture<ResponseMessage>

    @JsonNotification("aws/credentials/token/delete")
    fun deleteTokenCredentials(): CompletableFuture<Unit>

    @JsonRequest("aws/getConfigurationFromServer")
    fun getConfigurationFromServer(params: GetConfigurationFromServerParams): CompletableFuture<LspServerConfigurations>

    @JsonRequest("aws/updateConfiguration")
    fun updateConfiguration(params: UpdateConfigurationParams): CompletableFuture<LspServerConfigurations>
}
