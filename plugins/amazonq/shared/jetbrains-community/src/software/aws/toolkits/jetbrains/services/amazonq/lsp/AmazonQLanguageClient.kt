// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LSPAny
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_OPEN_TAB
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_CONTEXT_COMMANDS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_UPDATE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GET_SERIALIZED_CHAT_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OPEN_FILE_DIFF
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenFileDiffParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SHOW_SAVE_FILE_DIALOG_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowSaveFileDialogParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowSaveFileDialogResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import java.util.concurrent.CompletableFuture

/**
 * Requests sent by server to client
 */
@Suppress("unused")
interface AmazonQLanguageClient : LanguageClient {
    @JsonRequest("aws/credentials/getConnectionMetadata")
    fun getConnectionMetadata(): CompletableFuture<ConnectionMetadata>

    @JsonRequest(CHAT_OPEN_TAB)
    fun openTab(params: LSPAny): CompletableFuture<LSPAny>

    @JsonRequest(SHOW_SAVE_FILE_DIALOG_REQUEST_METHOD)
    fun showSaveFileDialog(params: ShowSaveFileDialogParams): CompletableFuture<ShowSaveFileDialogResult>

    @JsonRequest(GET_SERIALIZED_CHAT_REQUEST_METHOD)
    fun getSerializedChat(params: LSPAny): CompletableFuture<GetSerializedChatResult>

    @JsonNotification(CHAT_SEND_UPDATE)
    fun sendChatUpdate(params: LSPAny): CompletableFuture<Unit>

    @JsonNotification(OPEN_FILE_DIFF)
    fun openFileDiff(params: OpenFileDiffParams): CompletableFuture<Unit>

    @JsonNotification(CHAT_SEND_CONTEXT_COMMANDS)
    fun sendContextCommands(params: LSPAny): CompletableFuture<Unit>
}
