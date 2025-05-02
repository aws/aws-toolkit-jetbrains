// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.ProgressParams
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.logoutFromSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import software.aws.toolkits.jetbrains.core.gettingstarted.editor.checkBearerConnectionValidity
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ProgressNotificationUtils.getObject
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.AuthFollowUpClickedParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import java.util.UUID
import java.util.concurrent.CompletableFuture
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

    fun handleAuthFollowUpClicked(project: Project, params: AuthFollowUpClickedParams) {
        val incomingType = params.authFollowUpType
        val connectionManager = ToolkitConnectionManager.getInstance(project)
        try {
            when (incomingType) {
                AuthFollowUpType.RE_AUTH.value, AuthFollowUpType.MISSING_SCOPES.value -> {
                    connectionManager.activeConnectionForFeature(QConnection.getInstance())?.let {
                        reauthConnectionIfNeeded(project, it, isReAuth = true)
                    }
                    return
                }
                AuthFollowUpType.FULL_AUTH.value, AuthFollowUpType.USE_SUPPORTED_AUTH.value -> {
                    // Logout by deleting token credentials
                    val validConnection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q)
                    val connection = validConnection.activeConnectionBearer
                    if (connection != null) {
                        logoutFromSsoConnection(project, connection)
                    } else {
                        LOG.warn { "No valid connection found for logout" }
                    }
                    return
                }
                else -> {
                    LOG.warn { "Unknown auth follow up type: $incomingType" }
                    throw IllegalStateException("Error occurred while attempting to handle auth follow up: Unknown AuthFollowUpType $incomingType")
                }
            }
        } catch (ex: Exception) {
            LOG.warn(ex) { "Failed to handle authentication when auth follow up clicked" }
            throw ex
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<ChatCommunicationManager>()

        val LOG = getLogger<ChatCommunicationManager>()

        val pendingSerializedChatRequests = ConcurrentHashMap<String, CompletableFuture<GetSerializedChatResult>>()
        fun completeSerializedChatResponse(requestId: String, content: String) {
            pendingSerializedChatRequests.remove(requestId)?.complete(GetSerializedChatResult((content)))
        }

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
