// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan.controller

import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildClearPromptProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildPromptProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.ChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CodeScanChatMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CodeScanChatMessageContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.UpdatePlaceholderMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import java.util.UUID

class CodeScanChatHelper(
    private val messagePublisher: MessagePublisher,
    private val chatSessionStorage: ChatSessionStorage,
) {
    private var activeCodeScanTabId: String? = null

    fun setActiveCodeScanTabId(tabId: String) {
        activeCodeScanTabId = tabId
    }

    fun getActiveCodeScanTabId(): String? = activeCodeScanTabId

    private fun isInValidSession() = activeCodeScanTabId == null || chatSessionStorage.getSession(activeCodeScanTabId as String).isAuthenticating

    suspend fun addNewMessage(
        content: CodeScanChatMessageContent,
        messageIdOverride: String? = null,
        clearPreviousItemButtons: Boolean? = false,
    ) {
        if (isInValidSession()) return
        messagePublisher.publish(
            CodeScanChatMessage(
                tabId = activeCodeScanTabId as String,
                messageId = messageIdOverride ?: UUID.randomUUID().toString(),
                messageType = content.type,
                message = content.message,
                buttons = content.buttons,
                formItems = content.formItems,
                followUps = content.followUps,
                canBeVoted = content.canBeVoted,
                isLoading = content.type == ChatMessageType.AnswerPart,
                clearPreviousItemButtons = clearPreviousItemButtons as Boolean
            )
        )
    }

    suspend fun updateProgress(isProject: Boolean = false, isCanceling: Boolean = false) {
        if (isInValidSession()) return
        messagePublisher.publish(buildPromptProgressMessage(activeCodeScanTabId as String, isProject, isCanceling))
        sendChatInputEnabledMessage(false)
    }

    suspend fun clearProgress() {
        if (isInValidSession()) return
        messagePublisher.publish(buildClearPromptProgressMessage(activeCodeScanTabId as String))
        sendChatInputEnabledMessage(true)
    }

    suspend fun sendChatInputEnabledMessage(isEnabled: Boolean) {
        if (isInValidSession()) return
        messagePublisher.publish(ChatInputEnabledMessage(activeCodeScanTabId as String, enabled = isEnabled))
    }

    suspend fun updatePlaceholder(newPlaceholder: String) {
        if (isInValidSession()) return

        messagePublisher.publish(
            UpdatePlaceholderMessage(
                tabId = activeCodeScanTabId as String,
                newPlaceholder = newPlaceholder
            )
        )
    }
}
