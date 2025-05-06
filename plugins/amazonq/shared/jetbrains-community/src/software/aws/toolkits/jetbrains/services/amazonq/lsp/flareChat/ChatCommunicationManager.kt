// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.ProgressParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ProgressNotificationUtils.getObject
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ErrorParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_ERROR_PARAMS
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ChatCommunicationManager {
    private val chatPartialResultMap = ConcurrentHashMap<String, String>()
    private fun getPartialChatMessage(partialResultToken: String): String? =
        chatPartialResultMap.getOrDefault(partialResultToken, null)

    private val inflightRequestByTabId = ConcurrentHashMap<String, CompletableFuture<String>>()

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

    fun removePartialChatMessage(partialResultToken: String) =
        chatPartialResultMap.remove(partialResultToken)

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
            val uiMessage = convertToJsonToSendToChat(
                command = SEND_CHAT_COMMAND_PROMPT,
                tabId = tabId,
                params = partialChatResult,
                isPartialResult = true
            )
            AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
        }
    }

    fun getErrorUiMessage(tabId: String, exception: Exception, token: String?): String {
        token?.let {
            removePartialChatMessage(it)
        }
        val errorTitle = "An error occurred while processing your request."
        val errorMessage = "Details: ${exception.message}"
        val errorParams = Gson().toJson(ErrorParams(tabId, null, errorMessage, errorTitle)).toString()
        val uiMessage =  """
                {
                "command":"$CHAT_ERROR_PARAMS",
                "tabId": "$tabId",
                "params": "$errorParams",
                "isPartialResult": "true"
                }
            """.trimIndent()
        return uiMessage
    }


    companion object {
        fun getInstance(project: Project) = project.service<ChatCommunicationManager>()

        val pendingSerializedChatRequests = ConcurrentHashMap<String, CompletableFuture<GetSerializedChatResult>>()
        fun completeSerializedChatResponse(requestId: String, content: String) {
            pendingSerializedChatRequests.remove(requestId)?.complete(GetSerializedChatResult((content)))
        }
        fun getErrorUiMessage(tabId: String, e: Exception): String = Gson().toJson(
            ErrorParams(
                title = "An error occurred while processing your request.",
                message = "Details: ${e.message}",
                tabID = tabId,
                triggerType = TODO(),
            )).toString()

        fun convertToJsonToSendToChat(command: String, tabId: String, params: String, isPartialResult: Boolean): String =
            """
                {
                "command":"$command",
                "tabId": "$tabId",
                "params": $params,
                "isPartialResult": $isPartialResult
                }
            """.trimIndent()

        val pendingTabRequests = ConcurrentHashMap<String, CompletableFuture<OpenTabResult>>()

        fun completeTabOpen(requestId: String, tabId: String) {
            pendingTabRequests.remove(requestId)?.complete(OpenTabResult(tabId))
        }

        inline fun <reified T> convertNotificationToJsonForChat(command: String, params: T? = null) =
            """
    {
    "command":"$command",
    "params": ${if (params != null) Gson().toJson(params) else "{}"}
    }
            """.trimIndent()
    }
}
