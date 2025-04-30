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
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_BUTTON_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_COPY_CODE_TO_CLIPBOARD_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FEEDBACK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FILE_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FOLLOW_UP_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INFO_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INSERT_TO_CURSOR_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_QUICK_ACTION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_READY
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SOURCE_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_ADD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_REMOVE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CopyCodeToClipboardParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedQuickActionChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FeedbackParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FileClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FollowUpClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InfoLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InsertToCursorPositionParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PROMPT_INPUT_OPTIONS_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PromptInputOptionChangeParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SourceLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabEventParams
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

    @JsonRequest(SEND_CHAT_COMMAND_PROMPT)
    fun sendChatPrompt(params: EncryptedChatParams): CompletableFuture<String>

    @JsonNotification(CHAT_COPY_CODE_TO_CLIPBOARD_NOTIFICATION)
    fun copyCodeToClipboard(params: CopyCodeToClipboardParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_TAB_ADD)
    fun tabAdd(params: TabEventParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_TAB_REMOVE)
    fun tabRemove(params: TabEventParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_TAB_CHANGE)
    fun tabChange(params: TabEventParams): CompletableFuture<Unit>

    @JsonRequest(CHAT_QUICK_ACTION)
    fun sendQuickAction(params: EncryptedQuickActionChatParams): CompletableFuture<String>

    @JsonNotification(CHAT_INSERT_TO_CURSOR_NOTIFICATION)
    fun insertToCursorPosition(params: InsertToCursorPositionParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_FEEDBACK)
    fun feedback(params: FeedbackParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_READY)
    fun chatReady(): CompletableFuture<Unit>

    @JsonNotification(CHAT_LINK_CLICK)
    fun linkClick(params: LinkClickParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_INFO_LINK_CLICK)
    fun infoLinkClick(params: InfoLinkClickParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_SOURCE_LINK_CLICK)
    fun sourceLinkClick(params: SourceLinkClickParams): CompletableFuture<Unit>

    @JsonNotification(PROMPT_INPUT_OPTIONS_CHANGE)
    fun promptInputOptionsChange(params: PromptInputOptionChangeParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_FOLLOW_UP_CLICK)
    fun followUpClick(params: FollowUpClickParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_FILE_CLICK)
    fun fileClick(params: FileClickParams): CompletableFuture<Unit>

    @JsonRequest(CHAT_BUTTON_CLICK)
    fun buttonClick(params: ButtonClickParams): CompletableFuture<ButtonClickResult>
}
