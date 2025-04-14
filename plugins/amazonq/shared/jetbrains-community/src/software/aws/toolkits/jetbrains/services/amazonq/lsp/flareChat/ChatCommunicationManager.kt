// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.docker.agent.util.toJson
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.ProgressParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ProgressNotificationUtils.getObject
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_ERROR_PARAMS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ErrorParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


@Service(Service.Level.PROJECT)
class ChatCommunicationManager {
    private val chatPartialResultMap = ConcurrentHashMap<String, String>()
    private fun getPartialChatMessage(partialResultToken: String): String =
        chatPartialResultMap.getValue(partialResultToken)

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
        if (tabId == null || tabId.isEmpty()) {
            return
        }
        try {
            if (params.value.isLeft || params.value.right == null) {
                error(
                    "Error handling partial result notification: expected value of type Object"
                )
            }

            val encryptedPartialChatResult = getObject(params, String::class.java)
            if (encryptedPartialChatResult != null) {
                val partialChatResult = AmazonQLspService.getInstance(project).encryptionManager.decrypt(encryptedPartialChatResult)
                sendErrorToUi(tabId, IllegalStateException("Try out an error"), token)
  //              sendMessageToChatUi(SEND_CHAT_COMMAND_PROMPT, tabId, partialChatResult, isPartialResult = true)
            }
        } catch (e: Exception) {
            sendErrorToUi(tabId, e, token)
        }

    }

    private fun sendMessageToChatUi(command: String, tabId: String, partialChatResult: String, isPartialResult: Boolean) {
        val uiMessage = convertToJsonToSendToChat(
            command = command,
            tabId = tabId,
            params = partialChatResult,
            isPartialResult = isPartialResult
        )
        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
    }


    fun sendErrorToUi(tabId: String, exception: Exception, token: String) {
        removePartialChatMessage(token)
        val errorTitle = "An error occurred while processing your request."
        val errorMessage = "Details: ${exception.message}"
        val errorParams = Gson().toJsonTree(ErrorParams(tabId, null, errorMessage, errorTitle))
        sendErrorMessageToChatUi(CHAT_ERROR_PARAMS, tabId, errorParams, false)
    }

    private fun sendErrorMessageToChatUi(command: String, tabId: String, partialChatResult: JsonElement, isPartialResult: Boolean) {
        val uiMessage =  """
                {
                "command":"$command",
                "tabId": "$tabId",
                "params": $partialChatResult,
                "isPartialResult": $isPartialResult
                }
            """.trimIndent()
        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
    }
    companion object {
        fun getInstance(project: Project) = project.service<ChatCommunicationManager>()

        fun convertToJsonToSendToChat(command: String, tabId: String, params: String, isPartialResult: Boolean): String {
            if(command == CHAT_ERROR_PARAMS) {
                val param = JsonParser.parseString(params)
                return """
                {
                "command":"$command",
                "tabId": "$tabId",
                "params": $param,
                "isPartialResult": $isPartialResult
                }
            """.trimIndent()
            }
            return """
                {
                "command":"$command",
                "tabId": "$tabId",
                "params": $params,
                "isPartialResult": $isPartialResult
                }
            """.trimIndent()
        }

    }
}
