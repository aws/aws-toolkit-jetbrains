// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.webview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefJSQuery.Response
import kotlinx.coroutines.CancellationException
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
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.apps.AppConnection
import software.aws.toolkits.jetbrains.services.amazonq.commands.MessageSerializer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQChatServer
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.JsonRpcMethod
import software.aws.toolkits.jetbrains.services.amazonq.lsp.JsonRpcNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.JsonRpcRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsServerCapabilitiesProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatAsyncResultManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.AUTH_FOLLOW_UP_CLICKED
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.AuthFollowUpClickNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ButtonClickResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_BUTTON_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_CONVERSATION_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_COPY_CODE_TO_CLIPBOARD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_CREATE_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_DISCLAIMER_ACKNOWLEDGED
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FEEDBACK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FILE_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_FOLLOW_UP_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INFO_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_INSERT_TO_CURSOR
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_LIST_CONVERSATIONS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_OPEN_TAB
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_PROMPT_OPTION_ACKNOWLEDGED
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_QUICK_ACTION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_READY
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SOURCE_LINK_CLICK
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_ADD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_BAR_ACTIONS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_TAB_REMOVE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.EncryptedQuickActionChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GET_SERIALIZED_CHAT_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResponse
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.LIST_MCP_SERVERS_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.MCP_SERVER_CLICK_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OPEN_SETTINGS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OPEN_WORKSPACE_SETTINGS_KEY
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenSettingsNotification
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResponse
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResultError
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResultSuccess
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.PROMPT_INPUT_OPTIONS_CHANGE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.QuickChatActionRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.STOP_CHAT_RESPONSE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SendChatPromptRequest
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.StopResponseMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.TELEMETRY_EVENT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString
import software.aws.toolkits.jetbrains.services.amazonq.util.command
import software.aws.toolkits.jetbrains.services.amazonq.util.tabType
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.AmazonQTheme
import software.aws.toolkits.jetbrains.services.amazonq.webview.theme.ThemeBrowserAdapter
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.auth.isCodeScanAvailable
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.auth.isCodeTestAvailable
import software.aws.toolkits.jetbrains.services.amazonqDoc.auth.isDocAvailable
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.auth.isFeatureDevAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable
import software.aws.toolkits.jetbrains.services.codewhisperer.settings.CodeWhispererConfigurable
import software.aws.toolkits.jetbrains.settings.MeetQSettings
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Telemetry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.function.Function

class BrowserConnector(
    private val serializer: MessageSerializer = MessageSerializer.getInstance(),
    private val themeBrowserAdapter: ThemeBrowserAdapter = ThemeBrowserAdapter(),
    private val project: Project,
) {
    val uiReady = CompletableDeferred<Boolean>()
    private val chatCommunicationManager = ChatCommunicationManager.getInstance(project)
    private val chatAsyncResultManager = ChatAsyncResultManager.getInstance(project)

    suspend fun connect(
        browser: Browser,
        connections: List<AppConnection>,
    ) = coroutineScope {
        // Send browser messages to the outbound publisher
        addMessageHook(browser)
            .onEach { json ->
                val node = serializer.toNode(json)
                when (node.command) {
                    // this is sent when the named agents UI is ready
                    "ui-is-ready" -> {
                        uiReady.complete(true)
                        chatCommunicationManager.setUiReady()
                        RunOnceUtil.runOnceForApp("AmazonQ-UI-Ready") {
                            MeetQSettings.getInstance().reinvent2024OnboardingCount += 1
                        }
                    }
                    CHAT_DISCLAIMER_ACKNOWLEDGED -> {
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
                if (tabType == null || tabType == "cwc") {
                    handleFlareChatMessages(browser, node)
                } else {
                    connections.filter { connection -> connection.app.tabTypes.contains(tabType) }.forEach { connection ->
                        launch {
                            val message = serializer.deserialize(node, connection.messageTypeRegistry)
                            connection.messagesFromUiToApp.publish(message)
                        }
                    }
                }
            }
            .launchIn(this)

        // Wait for UI ready before starting to send messages to the UI.
        uiReady.await()

        // Chat options including history and quick actions need to be sent in once UI is ready
        updateQuickActionsInBrowser(browser)

        // Send inbound messages to the browser
        val inboundMessages = connections.map { it.messagesFromAppToUi.flow }.merge()
        inboundMessages
            .onEach { browser.postChat(serializer.serialize(it)) }
            .launchIn(this)
    }

    suspend fun connectTheme(
        chatBrowser: CefBrowser,
        themeSource: Flow<AmazonQTheme>,
    ) = coroutineScope {
        themeSource
            .distinctUntilChanged()
            .onEach {
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
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                val textDocumentIdentifier = editor?.let { TextDocumentIdentifier(toUriString(it.virtualFile)) }
                val cursorState = editor?.let { LspEditorUtil.getCursorState(it) }

                val enrichmentParams = mapOf(
                    "textDocument" to textDocumentIdentifier,
                    "cursorState" to cursorState,
                )

                val serializedEnrichmentParams = serializer.objectMapper.valueToTree<ObjectNode>(enrichmentParams)
                val chatParams: ObjectNode = (node.params as ObjectNode)
                    .setAll(serializedEnrichmentParams)

                val tabId = requestFromUi.params.tabId
                val partialResultToken = chatCommunicationManager.addPartialChatMessage(tabId)
                chatCommunicationManager.registerPartialResultToken(partialResultToken)

                var encryptionManager: JwtEncryptionManager? = null
                val result = AmazonQLspService.executeIfRunning(project) { server ->
                    encryptionManager = this.encryptionManager

                    val encryptedParams = EncryptedChatParams(this.encryptionManager.encrypt(chatParams), partialResultToken)
                    rawEndpoint.request(SEND_CHAT_COMMAND_PROMPT, encryptedParams) as CompletableFuture<String>
                } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))

                // We assume there is only one outgoing request per tab because the input is
                // blocked when there is an outgoing request
                chatCommunicationManager.setInflightRequestForTab(tabId, result)
                showResult(result, partialResultToken, tabId, encryptionManager, browser)
            }

            CHAT_QUICK_ACTION -> {
                val requestFromUi = serializer.deserializeChatMessages<QuickChatActionRequest>(node)
                val tabId = requestFromUi.params.tabId
                val quickActionParams = node.params ?: error("empty payload")
                val partialResultToken = chatCommunicationManager.addPartialChatMessage(tabId)
                chatCommunicationManager.registerPartialResultToken(partialResultToken)
                var encryptionManager: JwtEncryptionManager? = null
                val result = AmazonQLspService.executeIfRunning(project) { server ->
                    encryptionManager = this.encryptionManager

                    val encryptedParams = EncryptedQuickActionChatParams(this.encryptionManager.encrypt(quickActionParams), partialResultToken)
                    rawEndpoint.request(CHAT_QUICK_ACTION, encryptedParams) as CompletableFuture<String>
                } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))

                // We assume there is only one outgoing request per tab because the input is
                // blocked when there is an outgoing request
                chatCommunicationManager.setInflightRequestForTab(tabId, result)

                showResult(result, partialResultToken, tabId, encryptionManager, browser)
            }

            CHAT_LIST_CONVERSATIONS -> {
                handleChat(AmazonQChatServer.listConversations, node)
                    .whenComplete { response, _ ->
                        browser.postChat(
                            FlareUiMessage(
                                command = CHAT_LIST_CONVERSATIONS,
                                params = response
                            )
                        )
                    }
            }

            CHAT_CONVERSATION_CLICK -> {
                handleChat(AmazonQChatServer.conversationClick, node)
                    .whenComplete { response, _ ->
                        browser.postChat(
                            FlareUiMessage(
                                command = CHAT_CONVERSATION_CLICK,
                                params = response
                            )
                        )
                    }
            }

            CHAT_FEEDBACK -> {
                handleChat(AmazonQChatServer.feedback, node)
            }

            CHAT_READY -> {
                handleChat(AmazonQChatServer.chatReady, node) { params, invoke ->
                    uiReady.complete(true)
                    chatCommunicationManager.setUiReady()
                    RunOnceUtil.runOnceForApp("AmazonQ-UI-Ready") {
                        MeetQSettings.getInstance().reinvent2024OnboardingCount += 1
                    }

                    invoke()
                }
            }

            CHAT_TAB_ADD -> {
                handleChat(AmazonQChatServer.tabAdd, node) { params, invoke ->
                    // Track the tab ID when a tab is added
                    chatCommunicationManager.addTabId(params.tabId)
                    invoke()
                }
            }

            CHAT_TAB_REMOVE -> {
                handleChat(AmazonQChatServer.tabRemove, node) { params, invoke ->
                    chatCommunicationManager.removePartialChatMessage(params.tabId)
                    cancelInflightRequests(params.tabId)
                    // Remove the tab ID from tracking when a tab is removed
                    chatCommunicationManager.removeTabId(params.tabId)
                    invoke()
                }
            }

            CHAT_TAB_CHANGE -> {
                handleChat(AmazonQChatServer.tabChange, node)
            }

            CHAT_OPEN_TAB -> {
                val response = serializer.deserializeChatMessages<OpenTabResponse>(node)
                val future = chatCommunicationManager.removeTabOpenRequest(response.requestId) ?: return
                try {
                    val id = serializer.deserializeChatMessages<OpenTabResultSuccess>(node.params).result.tabId
                    future.complete(OpenTabResult(id))
                } catch (e: Exception) {
                    try {
                        val err = serializer.deserializeChatMessages<OpenTabResultError>(node.params)
                        future.complete(err.error)
                    } catch (_: Exception) {
                        future.completeExceptionally(e)
                    }
                }
            }

            CHAT_INSERT_TO_CURSOR -> {
                handleChat(AmazonQChatServer.insertToCursorPosition, node)
            }

            CHAT_LINK_CLICK -> {
                handleChat(AmazonQChatServer.linkClick, node)
            }

            CHAT_INFO_LINK_CLICK -> {
                handleChat(AmazonQChatServer.infoLinkClick, node)
            }

            CHAT_SOURCE_LINK_CLICK -> {
                handleChat(AmazonQChatServer.sourceLinkClick, node)
            }

            CHAT_FILE_CLICK -> {
                handleChat(AmazonQChatServer.fileClick, node)
            }

            PROMPT_INPUT_OPTIONS_CHANGE -> {
                handleChat(AmazonQChatServer.promptInputOptionsChange, node)
            }

            CHAT_FOLLOW_UP_CLICK -> {
                handleChat(AmazonQChatServer.followUpClick, node)
            }

            CHAT_BUTTON_CLICK -> {
                handleChat(AmazonQChatServer.buttonClick, node).thenApply { response ->
                    if (response is ButtonClickResult && !response.success) {
                        LOG.warn { "Failed to execute action associated with button with reason: ${response.failureReason}" }
                    }
                }
            }

            CHAT_COPY_CODE_TO_CLIPBOARD -> {
                handleChat(AmazonQChatServer.copyCodeToClipboard, node)
            }

            GET_SERIALIZED_CHAT_REQUEST_METHOD -> {
                val response = serializer.deserializeChatMessages<GetSerializedChatResponse>(node)
                chatCommunicationManager.completeSerializedChatResponse(
                    response.requestId,
                    response.params.result.content
                )
            }

            CHAT_TAB_BAR_ACTIONS -> {
                handleChat(AmazonQChatServer.tabBarActions, node) { params, invoke ->
                    invoke()
                        .whenComplete { actions, error ->
                            try {
                                if (error != null) {
                                    throw error
                                }

                                browser.postChat(
                                    FlareUiMessage(
                                        command = CHAT_TAB_BAR_ACTIONS,
                                        params = actions
                                    )
                                )
                            } catch (e: Exception) {
                                val cause = if (e is CompletionException) e.cause else e

                                // dont post error to UI if user cancels export
                                if (cause is ResponseErrorException && cause.responseError.code == ResponseErrorCode.RequestCancelled.getValue()) {
                                    return@whenComplete
                                }
                                LOG.error { "Failed to perform chat tab bar action $e" }
                                params.tabId?.let {
                                    browser.postChat(chatCommunicationManager.getErrorUiMessage(it, e, null))
                                }
                            }
                        }
                }
            }

            CHAT_CREATE_PROMPT -> {
                handleChat(AmazonQChatServer.createPrompt, node)
            }

            STOP_CHAT_RESPONSE -> {
                val stopResponseRequest = serializer.deserializeChatMessages<StopResponseMessage>(node)
                if (!chatCommunicationManager.hasInflightRequest(stopResponseRequest.params.tabId)) {
                    return
                }
                cancelInflightRequests(stopResponseRequest.params.tabId)
                chatCommunicationManager.removePartialChatMessage(stopResponseRequest.params.tabId)
            }

            AUTH_FOLLOW_UP_CLICKED -> {
                val message = serializer.deserializeChatMessages<AuthFollowUpClickNotification>(node)
                chatCommunicationManager.handleAuthFollowUpClicked(
                    project,
                    message.params
                )
            }

            CHAT_PROMPT_OPTION_ACKNOWLEDGED -> {
                val acknowledgedMessage = node.params?.get("messageId")
                if (acknowledgedMessage?.asText() == "programmerModeCardId") {
                    MeetQSettings.getInstance().pairProgrammingAcknowledged = true
                }
            }

            OPEN_SETTINGS -> {
                val openSettingsNotification = serializer.deserializeChatMessages<OpenSettingsNotification>(node)
                if (openSettingsNotification.params.settingKey != OPEN_WORKSPACE_SETTINGS_KEY) return
                runInEdt {
                    ShowSettingsUtil.getInstance().showSettingsDialog(browser.project, CodeWhispererConfigurable::class.java)
                }
            }
            TELEMETRY_EVENT -> {
                handleChat(AmazonQChatServer.telemetryEvent, node)
            }
            LIST_MCP_SERVERS_REQUEST_METHOD -> {
                handleChat(AmazonQChatServer.listMcpServers, node)
                    .whenComplete { response, _ ->
                        browser.postChat(
                            FlareUiMessage(
                                command = LIST_MCP_SERVERS_REQUEST_METHOD,
                                params = response
                            )
                        )
                    }
            }
            MCP_SERVER_CLICK_REQUEST_METHOD -> {
                handleChat(AmazonQChatServer.mcpServerClick, node)
                    .whenComplete { response, _ ->
                        browser.postChat(
                            FlareUiMessage(
                                command = MCP_SERVER_CLICK_REQUEST_METHOD,
                                params = response
                            )
                        )
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
            try {
                if (error != null) {
                    throw error
                }
                chatCommunicationManager.removePartialChatMessage(partialResultToken)
                val messageToChat = ChatCommunicationManager.convertToJsonToSendToChat(
                    SEND_CHAT_COMMAND_PROMPT,
                    tabId,
                    value?.let { encryptionManager?.decrypt(it) }.orEmpty(),
                    isPartialResult = false
                )
                browser.postChat(messageToChat)
                chatCommunicationManager.removeInflightRequestForTab(tabId)
            } catch (e: CancellationException) {
                LOG.warn { "Cancelled chat generation" }
                try {
                    chatAsyncResultManager.createRequestId(partialResultToken)
                    chatAsyncResultManager.getResult(partialResultToken)
                    handleCancellation(tabId, browser)
                } catch (ex: Exception) {
                    LOG.warn(ex) { "An error occurred while processing cancellation" }
                } finally {
                    chatAsyncResultManager.removeRequestId(partialResultToken)
                    chatCommunicationManager.removePartialResultLock(partialResultToken)
                    chatCommunicationManager.removeFinalResultProcessed(partialResultToken)
                }
            } catch (e: Exception) {
                LOG.warn(e) { "Failed to send chat message" }
                browser.postChat(chatCommunicationManager.getErrorUiMessage(tabId, e, partialResultToken))
            }
        }
    }

    private fun handleCancellation(tabId: String, browser: Browser) {
        // Send a message to hide the stop button without showing an error
        val cancelMessage = chatCommunicationManager.getCancellationUiMessage(tabId)
        browser.postChat(cancelMessage)
    }

    private fun updateQuickActionsInBrowser(browser: Browser) {
        val isFeatureDevAvailable = isFeatureDevAvailable(project)
        val isCodeTransformAvailable = isCodeTransformAvailable(project)
        val isDocAvailable = isDocAvailable(project)
        val isCodeScanAvailable = isCodeScanAvailable(project)
        val isCodeTestAvailable = isCodeTestAvailable(project)

        val script = """
            try {
                const tempConnector = connectorAdapter.initiateAdapter(
                    false, 
                    true, // the two values are not used here, needed for constructor
                    $isFeatureDevAvailable,
                    $isCodeTransformAvailable,
                    $isDocAvailable,
                    $isCodeScanAvailable,
                    $isCodeTestAvailable,
                    { postMessage: () => {} }
                );
                
                const commands = tempConnector.initialQuickActions?.slice(0, 2) || [];
                const options = ${Gson().toJson(AwsServerCapabilitiesProvider.getInstance(project).getChatOptions())};
                options.quickActions.quickActionsCommandGroups = [
                    ...commands,
                    ...options.quickActions.quickActionsCommandGroups
                ];
                
                window.postMessage({
                    command: "chatOptions",
                    params: options
                });
            } catch (e) {
                console.error("Error updating quick actions:", e);
            }
        """.trimIndent()

        browser.jcefBrowser.cefBrowser.executeJavaScript(script, browser.jcefBrowser.cefBrowser.url, 0)
    }

    private fun cancelInflightRequests(tabId: String) {
        chatCommunicationManager.getInflightRequestForTab(tabId)?.let { request ->
            request.cancel(true)
            chatCommunicationManager.removeInflightRequestForTab(tabId)
        }
    }

    private inline fun <reified Request, Response> handleChat(
        lspMethod: JsonRpcMethod<Request, Response>,
        node: JsonNode,
        crossinline serverAction: (params: Request, invokeService: () -> CompletableFuture<Response>) -> CompletableFuture<Response>,
    ): CompletableFuture<Response> {
        val requestFromUi = if (node.params == null) {
            Unit as Request
        } else {
            serializer.deserializeChatMessages<Request>(node.params, lspMethod.params)
        }

        return AmazonQLspService.executeIfRunning(project) { _ ->
            val invokeService = when (lspMethod) {
                is JsonRpcNotification<Request> -> {
                    // notify is Unit
                    { CompletableFuture.completedFuture(rawEndpoint.notify(lspMethod.name, node.params?.let { serializer.objectMapper.treeToValue<Any>(it) })) }
                }

                is JsonRpcRequest<Request, Response> -> {
                    {
                        rawEndpoint.request(lspMethod.name, node.params?.let { serializer.objectMapper.treeToValue<Any>(it) }).thenApply {
                            serializer.objectMapper.readValue(
                                Gson().toJson(it),
                                lspMethod.response
                            )
                        }
                    }
                }
            } as () -> CompletableFuture<Response>
            serverAction(requestFromUi, invokeService)
        } ?: CompletableFuture.failedFuture<Response>(IllegalStateException("LSP Server not running"))
    }

    private inline fun <reified Request, Response> handleChat(
        lspMethod: JsonRpcMethod<Request, Response>,
        node: JsonNode,
    ): CompletableFuture<Response> = handleChat(
        lspMethod,
        node,
    ) { _, invokeService -> invokeService() }

    private val JsonNode.params
        get() = get("params")

    companion object {
        private val LOG = getLogger<BrowserConnector>()
    }
}
