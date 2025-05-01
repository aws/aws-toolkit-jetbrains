// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.ProgressParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ProgressNotificationUtils.getObject
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class ChatCommunicationManager {
    private val chatPartialResultMap = ConcurrentHashMap<String, String>()
    private val pendingTabRequests = ConcurrentHashMap<String, CompletableFuture<OpenTabResult>>()

    fun addPendingOpenTabRequest(requestId: String): CompletableFuture<OpenTabResult> {
        return CompletableFuture<OpenTabResult>().also { future ->
            pendingTabRequests[requestId] = future
            future.orTimeout(30000, TimeUnit.MILLISECONDS)
                .whenComplete { _, error ->
                    if (error != null) {
                        pendingTabRequests.remove(requestId)
                    }
                }
        }
    }

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

    fun completeTabOpen(requestId: String, tabId: String) {
        pendingTabRequests.remove(requestId)?.complete(OpenTabResult(tabId))
    }

    companion object {
        fun getInstance(project: Project) = project.service<ChatCommunicationManager>()

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
