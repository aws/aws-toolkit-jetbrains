// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ProgressNotificationUtils.getObject
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LSPAny
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.AuthFollowUpClickedParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.AuthFollowupType
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_ERROR_PARAMS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ErrorParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileDialog
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ChatCommunicationManager(private val project: Project, private val cs: CoroutineScope) {
    val uiReady = CompletableDeferred<Boolean>()
    private val chatPartialResultMap = ConcurrentHashMap<String, String>()
    private val inflightRequestByTabId = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val pendingSerializedChatRequests = ConcurrentHashMap<String, CompletableFuture<GetSerializedChatResult>>()
    private val pendingTabRequests = ConcurrentHashMap<String, CompletableFuture<LSPAny>>()
    private val openTabs = mutableSetOf<String>()

    fun setUiReady() {
        uiReady.complete(true)
    }

    private var chatUpdateCallback: ((FlareUiMessage) -> Unit)? = null

    fun setChatUpdateCallback(callback: (FlareUiMessage) -> Unit) {
        chatUpdateCallback = callback
    }

    fun notifyUi(uiMessage: FlareUiMessage) {
        cs.launch {
            uiReady.await()
            chatUpdateCallback?.invoke(uiMessage)
        }
    }

    fun setInflightRequestForTab(tabId: String, result: CompletableFuture<String>) {
        inflightRequestByTabId[tabId] = result
    }
    fun removeInflightRequestForTab(tabId: String) {
        inflightRequestByTabId.remove(tabId)
    }

    fun getInflightRequestForTab(tabId: String): CompletableFuture<String>? = inflightRequestByTabId[tabId]

    fun hasInflightRequest(tabId: String): Boolean = inflightRequestByTabId.containsKey(tabId)

    fun addPartialChatMessage(tabId: String): String {
        val partialResultToken: String = UUID.randomUUID().toString()
        chatPartialResultMap[partialResultToken] = tabId
        return partialResultToken
    }

    private fun getPartialChatMessage(partialResultToken: String): String? =
        chatPartialResultMap.getOrDefault(partialResultToken, null)

    fun removePartialChatMessage(partialResultToken: String) =
        chatPartialResultMap.remove(partialResultToken)

    fun addTabId(tabId: String) {
        synchronized(openTabs) {
            openTabs.add(tabId)
        }
    }

    fun removeTabId(tabId: String) {
        synchronized(openTabs) {
            openTabs.remove(tabId)
        }
    }

    fun getAllTabIds(): Set<String> {
        synchronized(openTabs) {
            return openTabs.toSet()
        }
    }

    fun addSerializedChatRequest(requestId: String, result: CompletableFuture<GetSerializedChatResult>) {
        pendingSerializedChatRequests[requestId] = result
    }

    fun completeSerializedChatResponse(requestId: String, content: String) {
        pendingSerializedChatRequests.remove(requestId)?.complete(GetSerializedChatResult((content)))
    }

    fun removeSerializedChatRequest(requestId: String) {
        pendingSerializedChatRequests.remove(requestId)
    }

    fun addTabOpenRequest(requestId: String, result: CompletableFuture<LSPAny>) {
        pendingTabRequests[requestId] = result
    }

    fun removeTabOpenRequest(requestId: String) =
        pendingTabRequests.remove(requestId)

    fun handlePartialResultProgressNotification(project: Project, params: ProgressParams) {
        val token = ProgressNotificationUtils.getToken(params)
        val tabId = getPartialChatMessage(token)
        if (tabId.isNullOrEmpty()) {
            return
        }
        if (params.value.isLeft || params.value.right == null) {
            error(
                "Error handling partial result notification: expected value of type Object"
            )
        }

        val encryptedPartialChatResult = getObject(params, String::class.java)
        if (encryptedPartialChatResult != null) {
            val partialChatResult = AmazonQLspService.getInstance(project).encryptionManager.decrypt(encryptedPartialChatResult)
            val partialResultMap: Any = tryOrNull {
                Gson().fromJson(partialChatResult, Map::class.java)
            } ?: partialChatResult

            notifyUi(
                FlareUiMessage(
                    command = SEND_CHAT_COMMAND_PROMPT,
                    tabId = tabId,
                    params = partialResultMap,
                    isPartialResult = true
                )
            )
        }
    }

    fun getErrorUiMessage(tabId: String, exception: Exception, token: String?): String {
        token?.let {
            removePartialChatMessage(it)
        }
        var errorMessage: String? = null
        if (exception is ResponseErrorException) {
            errorMessage = tryOrNull {
                "${exception.responseError.message}: ${Gson().fromJson(exception.responseError.data as JsonObject, ChatMessage::class.java).body}"
            } ?: exception.responseError.message
        }

        val errorTitle = "An error occurred while processing your request."
        errorMessage = errorMessage ?: "Details: ${exception.message}"
        val errorParams = Gson().toJson(ErrorParams(tabId, null, errorMessage, errorTitle)).toString()
        val isPartialResult = false
        val uiMessage = """
                {
                "command":"$CHAT_ERROR_PARAMS",
                "tabId": "$tabId",
                "params": $errorParams,
                "isPartialResult": $isPartialResult
                }
        """.trimIndent()
        return uiMessage
    }

    fun handleAuthFollowUpClicked(project: Project, params: AuthFollowUpClickedParams) {
        val incomingType = params.authFollowupType
        val connectionManager = ToolkitConnectionManager.getInstance(project)
        try {
            when (incomingType) {
                AuthFollowupType.USE_SUPPORTED_AUTH -> {
                    val activeProfile = QRegionProfileManager.getInstance().activeProfile(project)
                    if (activeProfile != null) {
                        project.messageBus.syncPublisher(QRegionProfileSelectedListener.TOPIC)
                            .onProfileSelected(project, QRegionProfileManager.getInstance().activeProfile(project))
                    } else {
                        QRegionProfileDialog(
                            project,
                            selectedProfile = null
                        ).show()
                    }

                    return
                }
                AuthFollowupType.RE_AUTH,
                AuthFollowupType.MISSING_SCOPES,
                AuthFollowupType.FULL_AUTH,
                -> {
                    connectionManager.activeConnectionForFeature(QConnection.getInstance())?.let {
                        reauthConnectionIfNeeded(project, it, isReAuth = true)
                    }
                    return
                }
                else -> {
                    LOG.warn { "Unknown auth follow up type: $incomingType" }
                }
            }
        } catch (ex: Exception) {
            LOG.warn(ex) { "Failed to handle authentication when auth follow up clicked" }
            throw ex
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<ChatCommunicationManager>()

        private val LOG = getLogger<ChatCommunicationManager>()

        fun convertToJsonToSendToChat(command: String, tabId: String, params: String, isPartialResult: Boolean): String =
            """
                {
                "command":"$command",
                "tabId": "$tabId",
                "params": $params,
                "isPartialResult": $isPartialResult
                }
            """.trimIndent()
    }
}
