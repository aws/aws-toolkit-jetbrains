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
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INFO_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_QUICK_ACTION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SOURCE_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_READY
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedQuickActionChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InfoLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SourceLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.UpdateCredentialsPayload
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

    @JsonRequest("aws/getConfigurationFromServer")
    fun getConfigurationFromServer(params: GetConfigurationFromServerParams): CompletableFuture<LspServerConfigurations>

    @JsonRequest("aws/updateConfiguration")
    fun updateConfiguration(params: UpdateConfigurationParams): CompletableFuture<LspServerConfigurations>

    @JsonRequest(SEND_CHAT_COMMAND_PROMPT)
    fun sendChatPrompt(params: EncryptedChatParams): CompletableFuture<String>

    @JsonRequest(CHAT_QUICK_ACTION)
    fun sendQuickAction(params: EncryptedQuickActionChatParams): CompletableFuture<String>

    @JsonNotification(CHAT_READY)
    fun chatReady(): CompletableFuture<Unit>

    @JsonNotification(CHAT_LINK_CLICK)
    fun linkClick(params: LinkClickParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_INFO_LINK_CLICK)
    fun infoLinkClick(params: InfoLinkClickParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_SOURCE_LINK_CLICK)
    fun sourceLinkClick(params: SourceLinkClickParams): CompletableFuture<Unit>
}
