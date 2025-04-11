// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageSerializer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.getTextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatPrompt
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorState
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EndChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_END_CHAT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendChatPromptRequest
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
                    "ui-is-ready" -> {
                        uiReady.complete(true)
                        RunOnceUtil.runOnceForApp("AmazonQ-UI-Ready") {
                            MeetQSettings.getInstance().reinvent2024OnboardingCount += 1
                        }
                    }

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
                val requestFromUi = serializer.deserializeChatMessages(node, SendChatPromptRequest::class.java)
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

                val partialResultToken = chatCommunicationManager.addPartialChatMessage(requestFromUi.params.tabId)
                val chatParams = ChatParams(
                    requestFromUi.params.tabId,
                    chatPrompt,
                    textDocumentIdentifier,
                    cursorState
                )

                var encryptionManager: JwtEncryptionManager? = null
                val result = AmazonQLspService.executeIfRunning(project) { server ->
                    encryptionManager = this.encryptionManager
                    encryptionManager?.encrypt(chatParams)?.let { EncryptedChatParams(it, partialResultToken) }?.let { server.sendChatPrompt(it) }
                } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))

                result.whenComplete {
                        value, error ->
                    chatCommunicationManager.removePartialChatMessage(partialResultToken)
                    val messageToChat = ChatCommunicationManager.convertToJsonToSendToChat(
                        node.command,
                        requestFromUi.params.tabId,
                        encryptionManager?.decrypt(value).orEmpty(),
                        isPartialResult = false
                    )
                    browser.postChat(messageToChat)
                }
            }

            SEND_END_CHAT -> {
                val requestFromUi = serializer.deserializeChatMessages(node, EndChatParams::class.java)

                val endChatParams = EndChatParams(requestFromUi.tabId)
                val result = AmazonQLspService.executeIfRunning(project) { server ->
                    server.endChat(endChatParams)
                } ?: CompletableFuture.failedFuture(IllegalStateException("LSP Server not running"))
            }
        }
    }
}
