// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.GetConfigurationFromServerParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.UpdateConfigurationParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.SyncModuleDependenciesParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.GetSsoTokenParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.GetSsoTokenResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.InvalidateSsoTokenParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.InvalidateSsoTokenResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.ListProfilesResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.SsoTokenChangedParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.UpdateProfileParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.identity.UpdateProfileResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.dependencies.DidChangeDependencyPathsParams
import java.util.concurrent.CompletableFuture

/**
 * Remote interface exposed by the Amazon Q language server
 */
@Suppress("unused")
interface AmazonQLanguageServer : LanguageServer {
    @JsonNotification("aws/didChangeDependencyPaths")
    fun didChangeDependencyPaths(params: DidChangeDependencyPathsParams): CompletableFuture<Unit>

    @JsonRequest("aws/credentials/token/update")
    fun updateTokenCredentials(payload: UpdateCredentialsPayload): CompletableFuture<ResponseMessage>

    @JsonNotification("aws/credentials/token/delete")
    fun deleteTokenCredentials(): CompletableFuture<Unit>

    @JsonRequest("aws/identity/listProfiles")
    fun listProfiles(): CompletableFuture<ListProfilesResult>

    @JsonRequest("aws/identity/getSsoToken")
    fun getSsoToken(params: GetSsoTokenParams): CompletableFuture<GetSsoTokenResult>

    @JsonRequest("aws/identity/invalidateSsoToken")
    fun invalidateSsoToken(params: InvalidateSsoTokenParams): CompletableFuture<InvalidateSsoTokenResult>

    @JsonRequest("aws/identity/updateProfile")
    fun updateProfile(params: UpdateProfileParams): CompletableFuture<UpdateProfileResult>

    @JsonNotification("aws/identity/ssoTokenChanged")
    fun ssoTokenChanged(params: SsoTokenChangedParams): CompletableFuture<Unit>

    @JsonRequest("aws/getConfigurationFromServer")
    fun getConfigurationFromServer(params: GetConfigurationFromServerParams): CompletableFuture<LspServerConfigurations>

    @JsonRequest("aws/updateConfiguration")
    fun updateConfiguration(params: UpdateConfigurationParams): CompletableFuture<LspServerConfigurations>
}
