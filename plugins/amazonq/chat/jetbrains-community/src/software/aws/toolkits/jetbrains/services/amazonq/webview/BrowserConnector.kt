// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.databind.JsonNode
import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefJSQuery.Response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageSerializer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLanguageServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsServerCapabilitiesProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.getTextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_BUTTON_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_COPY_CODE_TO_CLIPBOARD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FEEDBACK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FOLLOW_UP_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_GET_SERIALIZED_CHAT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INFO_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INSERT_TO_CURSOR
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_PROMPT_OPTION_ACKNOWLEDGED
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_QUICK_ACTION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_READY
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SOURCE_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_ADD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_BAR_ACTIONS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_REMOVE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatPrompt
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatReadyNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CopyCodeToClipboardNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CopyCodeToClipboardParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorState
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedQuickActionChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FeedbackNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FeedbackParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FollowUpClickNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FollowUpClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InfoLinkClickNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InfoLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InsertToCursorPositionNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.InsertToCursorPositionParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LinkClickNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PROMPT_INPUT_OPTIONS_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PromptInputOptionChangeNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PromptInputOptionChangeParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.QuickChatActionRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendChatPromptRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SourceLinkClickNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SourceLinkClickParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabBarActionParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabBarActionRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabEventParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TabEventRequest
import software.aws.toolkits.jetbrains.services.amazonq.util.command
import software.aws.toolkits.jetbrains.services.amazonq.util.tabType
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.AmazonQTheme
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.ThemeBrowserAdapter
import software.aws.toolkits.jetbrains.settings.MeetQSettings
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class BrowserConnector(
    private val serializer: MessageSerializer = MessageSerializer.getInstance(),
    private val themeBrowserAdapter: ThemeBrowserAdapter = ThemeBrowserAdapter(),
    private val project: Project,
) {
    var uiReady = CompletableDeferred<Boolean>()
    private val chatCommunicationManager = ChatCommunicationManager.getInstance(project)

    suspend fun connect(
        browser: Browser,
        connections: List<AppConnection>,
    ) = coroutineScope {
        // Send browser messages to the outbound publisher
        addMessageHook(browser)
            .onEach { json ->
                val node = serializer.toNode(json)
                when (node.command) {
                    "disclaimer-acknowledged" -> {
                        MeetQSettings.getInstance().disclaimerAcknowledged = true
                    }

                    // some weird issue preventing deserialization from working
                    "open-user-guide" -> {
                        BrowserUtil.browse(node.get("userGuideLink").asText())
                    }
                    "send-telemetry" -> {
                        val source = node.get("source")
                        val module = node.get("module")
                        val trigger = node.get("trigger")

                        if (source != null) {
                            Telemetry.ui.click.use {
                                it.elementId(source.asText())
                            }
                        } else if (module != null && trigger != null) {
                            Telemetry.toolkit.willOpenModule.use {
                                it.module(module.asText())
                                it.source(trigger.asText())
                                it.result(MetricResult.Succeeded)
                            }
                        }
                    }
                }

                val tabType = node.tabType
                if (tabType == null) {
                    handleFlareChatMessages(browser, node)
                }
                connections.filter { connection -> connection.app.tabTypes.contains(tabType) }.forEach { connection ->
                    launch {
                        val message = serializer.deserialize(node, connection.messageTypeRegistry)
                        connection.messagesFromUiToApp.publish(message)
                    }
                }
            }
            .launchIn(this)

        // Wait for UI ready before starting to send messages to the UI.
        uiReady.await()

        // Chat options including history and quick actions need to be sent in once UI is ready
        val showChatOptions = """{
            "command": "chatOptions",
            "params": ${Gson().toJson(AwsServerCapabilitiesProvider.getInstance(project).getChatOptions())}
            }
        """.trimIndent()
        browser.postChat(showChatOptions)

        // Send inbound messages to the browser
        val inboundMessages = connections.map { it.messagesFromAppToUi.flow }.merge()
        inboundMessages
            .onEach { browser.post(serializer.serialize(it)) }
            .launchIn(this)
    }

    suspend fun connectTheme(
        chatBrowser: CefBrowser,
        loginBrowser: CefBrowser,
        themeSource: Flow<AmazonQTheme>,
    ) = coroutineScope {
        themeSource
            .distinctUntilChanged()
            .onEach {
                themeBrowserAdapter.updateLoginThemeInBrowser(loginBrowser, it)
                themeBrowserAdapter.updateThemeInBrowser(chatBrowser, it, uiReady)
            }
            .launchIn(this)
    }

    private fun addMessageHook(browser: Browser) = callbackFlow {
        val handler = Function<String, Response> {
            trySend(it)
            Response(null)
        }

        browser.receiveMessageQuery.addHandler(handler)

        awaitClose {
            browser.receiveMessageQuery.removeHandler(handler)
        }
    }

    private fun handleFlareChatMessages(browser: Browser, node: JsonNode) {
        when (node.command) {
            SEND_CHAT_COMMAND_PROMPT -> {
                val requestFromUi = serializer.deserializeChatMessages<SendChatPromptRequest>(node)
                val chatPrompt = ChatPrompt(
                    requestFromUi.params.prompt.prompt,
                    requestFromUi.params.prompt.escapedPrompt,
                    node.command
                )
                val textDocumentIdentifier = getTextDocumentIdentifier(project)
                val cursorState = CursorState(
                    Range(
                        Position(
                            0,
                            0
                        ),
                        Position(
                            1,
                            1
                        )
                    )
                )

                val chatParams = ChatParams(
                    requestFromUi.params.tabId,
                    chatPrompt,
                    textDocumentIdentifier,
                    cursorState
                )

                val tabId = requestFromUi.params.tabId
                val partialResultToken = chatCommunicationManager.addPartialChatMessage(tabId)

                var encryptionManager: JwtEncryptionManager? = null
                val result = AmazonQLspService.executeIfRunning(project) { server ->
                    encryptionManager = this.encryptionManager
                    encryptionManager?.encrypt(chatParams)?.let { EncryptedChatParams(it, partialResultToken) }?.let { server.sendChatPrompt(it) }
                } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))
                showResult(result, partialResultToken, tabId, encryptionManager, browser)
            }
            CHAT_QUICK_ACTION -> {
                val requestFromUi = serializer.deserializeChatMessages<QuickChatActionRequest>(node)
                val tabId = requestFromUi.params.tabId
                val quickActionParams = requestFromUi.params
                val partialResultToken = chatCommunicationManager.addPartialChatMessage(tabId)
                var encryptionManager: JwtEncryptionManager? = null
                val result = AmazonQLspService.executeIfRunning(project) { server ->
                    encryptionManager = this.encryptionManager
                    encryptionManager?.encrypt(quickActionParams)?.let {
                        EncryptedQuickActionChatParams(it, partialResultToken)
                    }?.let {
                        server.sendQuickAction(it)
                    }
                } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))

                showResult(result, partialResultToken, tabId, encryptionManager, browser)
            }
            CHAT_FEEDBACK -> {
                handleChatNotification<FeedbackNotification, FeedbackParams>(node) { server, params ->
                    server.feedback(params)
                }
            }
            CHAT_READY -> {
                handleChatNotification<ChatReadyNotification, Unit>(node) { server, _ ->
                    uiReady.complete(true)
                    RunOnceUtil.runOnceForApp("AmazonQ-UI-Ready") {
                        MeetQSettings.getInstance().reinvent2024OnboardingCount += 1
                    }
                    server.chatReady()
                }
            }
            CHAT_TAB_ADD -> {
                handleChatNotification<TabEventRequest, TabEventParams>(node) { server, params ->
                    server.tabAdd(params)
                }
            }
            CHAT_TAB_REMOVE -> {
                handleChatNotification<TabEventRequest, TabEventParams>(node) { server, params ->
                    chatCommunicationManager.removePartialChatMessage(params.tabId)
                    server.tabRemove(params)
                }
            }
            CHAT_TAB_CHANGE -> {
                handleChatNotification<TabEventRequest, TabEventParams>(node) { server, params ->
                    server.tabChange(params)
                }
            }
            CHAT_INSERT_TO_CURSOR -> {
                handleChatNotification<InsertToCursorPositionNotification, InsertToCursorPositionParams>(node) { server, params ->
                    server.insertToCursorPosition(params)
                }
            }
            CHAT_LINK_CLICK -> {
                handleChatNotification<LinkClickNotification, LinkClickParams>(node) { server, params ->
                    server.linkClick(params)
                }
            }

            CHAT_INFO_LINK_CLICK -> {
                handleChatNotification<InfoLinkClickNotification, InfoLinkClickParams>(node) { server, params ->
                    server.infoLinkClick(params)
                }
            }

            CHAT_SOURCE_LINK_CLICK -> {
                handleChatNotification<SourceLinkClickNotification, SourceLinkClickParams>(node) { server, params ->
                    server.sourceLinkClick(params)
                }
            }

            PROMPT_INPUT_OPTIONS_CHANGE -> {
                handleChatNotification<PromptInputOptionChangeNotification, PromptInputOptionChangeParams>(node) {
                        server, params ->
                    server.promptInputOptionsChange(params)
                }
            }

            CHAT_PROMPT_OPTION_ACKNOWLEDGED -> {
                val acknowledgedMessage = node.get("params").get("messageId")
                if (acknowledgedMessage.asText() == "programmerModeCardId") {
                    MeetQSettings.getInstance().amazonQChatPairProgramming = false
                }
            }

            CHAT_FOLLOW_UP_CLICK -> {
                handleChatNotification<FollowUpClickNotification, FollowUpClickParams>(node) { server, params ->
                    server.followUpClick(params)
                }
            }
            CHAT_BUTTON_CLICK -> {
                handleChatNotification<ButtonClickNotification, ButtonClickParams>(node) { server, params ->
                    server.buttonClick(params)
                }.thenApply { response ->
                    if (response is ButtonClickResult && !response.success) {
                        LOG.warn { "Failed to execute action associated with button with reason: ${response.failureReason}" }
                    }
                }
            }
            CHAT_COPY_CODE_TO_CLIPBOARD -> {
                handleChatNotification<CopyCodeToClipboardNotification, CopyCodeToClipboardParams>(node) { server, params ->
                    server.copyCodeToClipboard(params)
                }
            }

            CHAT_GET_SERIALIZED_CHAT -> {
                handleChatNotification<GetSerializedChatRequest, GetSerializedChatParams>(node)
                {
                        server, params -> server.getSerializedActions(params)
                }
            }

            CHAT_TAB_BAR_ACTIONS -> {
                handleChatNotification<TabBarActionRequest, TabBarActionParams>(node)
                {
                    server, params -> server.tabBarActions(params)
                }

            }
        }
    }

    private fun showResult(
        result: CompletableFuture<String>,
        partialResultToken: String,
        tabId: String,
        encryptionManager: JwtEncryptionManager?,
        browser: Browser,
    ) {
        result.whenComplete { value, error ->
            chatCommunicationManager.removePartialChatMessage(partialResultToken)
            val messageToChat = ChatCommunicationManager.convertToJsonToSendToChat(
                SEND_CHAT_COMMAND_PROMPT,
                tabId,
                encryptionManager?.decrypt(value).orEmpty(),
                isPartialResult = false
            )
            browser.postChat(messageToChat)
        }
    }

    private inline fun <reified T, R> handleChatNotification(
        node: JsonNode,
        crossinline serverAction: (server: AmazonQLanguageServer, params: R) -> CompletableFuture<*>,
    ): CompletableFuture<*> where T : ChatNotification<R> {
        val requestFromUi = serializer.deserializeChatMessages<T>(node)
        return AmazonQLspService.executeIfRunning(project) { server ->
            serverAction(server, requestFromUi.params)
        } ?: CompletableFuture.failedFuture<Unit>(IllegalStateException("LSP Server not running"))
    }

    companion object {
        private val LOG = getLogger<BrowserConnector>()
    }
}
