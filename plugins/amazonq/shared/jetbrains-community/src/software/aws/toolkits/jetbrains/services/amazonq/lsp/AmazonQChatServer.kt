// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethodProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LSPAny
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_BUTTON_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_CONVERSATION_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_COPY_CODE_TO_CLIPBOARD_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_CREATE_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FEEDBACK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FILE_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FOLLOW_UP_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INFO_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INSERT_TO_CURSOR_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LIST_CONVERSATIONS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_QUICK_ACTION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_READY
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SOURCE_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_ADD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_BAR_ACTIONS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_REMOVE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ConversationClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CopyCodeToClipboardParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CreatePromptParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedQuickActionChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FeedbackParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FileClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FollowUpClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GET_SERIALIZED_CHAT_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InfoLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InsertToCursorPositionParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LIST_MCP_SERVERS_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ListConversationsParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.MCP_SERVER_CLICK_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PROMPT_INPUT_OPTIONS_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PromptInputOptionChangeParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SourceLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TELEMETRY_EVENT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabBarActionParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabEventParams
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMembers

sealed interface JsonRpcMethod<Request, Response> {
    val name: String
    val params: Class<Request>
}

data class JsonRpcNotification<Request>(
    override val name: String,
    override val params: Class<Request>,
) : JsonRpcMethod<Request, Unit>

@Suppress("FunctionNaming")
fun JsonRpcNotification(name: String) = JsonRpcNotification(name, Unit::class.java)

data class JsonRpcRequest<Request, Response>(
    override val name: String,
    override val params: Class<Request>,
    val response: Class<Response>,
) : JsonRpcMethod<Request, Response>

/**
 * Messaging for the Chat feature follows this pattern:
 *     Mynah-UI <-> Plugin <-> Flare LSP
 *
 * However, the default scenario is that the plugin only cares about a subset of request/response payload and should otherwise transparently passthrough data.
 * To obtain some semblance of type safety, we model the subset of values that are relevant and passthrough the rest.
 *
 * Generally, methods MUST be modeled here if the response type is needed, or LSP4J will return null
 */
object AmazonQChatServer : JsonRpcMethodProvider {
    override fun supportedMethods() = buildMap {
        AmazonQChatServer::class.declaredMembers.filter { it is KProperty }.forEach {
            val method = it.call(AmazonQChatServer) as JsonRpcMethod<*, *>

            // trick lsp4j into returning the complete message even if we didn't model it completely
            val lsp4jMethod = when (method) {
                is JsonRpcNotification<*> -> org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod.notification(method.name, Any::class.java)
                is JsonRpcRequest<*, *> -> org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod.request(method.name, Any::class.java, Any::class.java)
            }

            put(method.name, lsp4jMethod)
        }
    }

    val sendChatPrompt = JsonRpcRequest(
        SEND_CHAT_COMMAND_PROMPT,
        EncryptedChatParams::class.java,
        String::class.java
    )

    val sendQuickAction = JsonRpcRequest(
        CHAT_QUICK_ACTION,
        EncryptedQuickActionChatParams::class.java,
        String::class.java
    )

    val copyCodeToClipboard = JsonRpcNotification(
        CHAT_COPY_CODE_TO_CLIPBOARD_NOTIFICATION,
        CopyCodeToClipboardParams::class.java,
    )

    val chatReady = JsonRpcNotification(
        CHAT_READY,
    )

    val tabAdd = JsonRpcNotification(
        CHAT_TAB_ADD,
        TabEventParams::class.java
    )

    val tabRemove = JsonRpcNotification(
        CHAT_TAB_REMOVE,
        TabEventParams::class.java
    )

    val tabChange = JsonRpcNotification(
        CHAT_TAB_CHANGE,
        TabEventParams::class.java
    )

    val feedback = JsonRpcNotification(
        CHAT_FEEDBACK,
        FeedbackParams::class.java
    )

    val insertToCursorPosition = JsonRpcNotification(
        CHAT_INSERT_TO_CURSOR_NOTIFICATION,
        InsertToCursorPositionParams::class.java
    )

    val linkClick = JsonRpcNotification(
        CHAT_LINK_CLICK,
        LinkClickParams::class.java
    )

    val infoLinkClick = JsonRpcNotification(
        CHAT_INFO_LINK_CLICK,
        InfoLinkClickParams::class.java
    )

    val sourceLinkClick = JsonRpcNotification(
        CHAT_SOURCE_LINK_CLICK,
        SourceLinkClickParams::class.java
    )

    val promptInputOptionsChange = JsonRpcNotification(
        PROMPT_INPUT_OPTIONS_CHANGE,
        PromptInputOptionChangeParams::class.java
    )

    val followUpClick = JsonRpcNotification(
        CHAT_FOLLOW_UP_CLICK,
        FollowUpClickParams::class.java
    )

    val fileClick = JsonRpcNotification(
        CHAT_FILE_CLICK,
        FileClickParams::class.java
    )

    val listConversations = JsonRpcRequest(
        CHAT_LIST_CONVERSATIONS,
        ListConversationsParams::class.java,
        Any::class.java
    )

    val listMcpServers = JsonRpcRequest(
        LIST_MCP_SERVERS_REQUEST_METHOD,
        LSPAny::class.java,
        LSPAny::class.java
    )

    val mcpServerClick = JsonRpcRequest(
        MCP_SERVER_CLICK_REQUEST_METHOD,
        LSPAny::class.java,
        LSPAny::class.java
    )

    val conversationClick = JsonRpcRequest(
        CHAT_CONVERSATION_CLICK,
        ConversationClickParams::class.java,
        Any::class.java
    )

    val buttonClick = JsonRpcRequest(
        CHAT_BUTTON_CLICK,
        ButtonClickParams::class.java,
        ButtonClickResult::class.java
    )

    val tabBarActions = JsonRpcRequest(
        CHAT_TAB_BAR_ACTIONS,
        TabBarActionParams::class.java,
        Any::class.java
    )

    val getSerializedActions = JsonRpcRequest(
        GET_SERIALIZED_CHAT_REQUEST_METHOD,
        GetSerializedChatParams::class.java,
        GetSerializedChatResult::class.java
    )

    val createPrompt = JsonRpcNotification(
        CHAT_CREATE_PROMPT,
        CreatePromptParams::class.java
    )

    val telemetryEvent = JsonRpcNotification(
        TELEMETRY_EVENT,
        Any::class.java
    )
}
