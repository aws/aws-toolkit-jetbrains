// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller

import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.ChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestAddAnswerMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestChatMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestChatMessageContent
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestUpdateAnswerMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestUpdateUIMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.ProgressField
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.PromptProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.UpdatePlaceholderMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.Session
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.FollowUp
import software.aws.toolkits.resources.message
import java.util.UUID

class CodeTestChatHelper(
    private val messagePublisher: MessagePublisher,
    private val chatSessionStorage: ChatSessionStorage,
) {
    private var activeCodeTestTabId: String = ""

    fun setActiveCodeTestTabId(tabId: String) {
        activeCodeTestTabId = tabId
    }

    fun getActiveCodeTestTabId(): String = activeCodeTestTabId

    fun getSession(tabId: String): Session = chatSessionStorage.getSession(tabId)

    fun deleteSession(tabId: String) = chatSessionStorage.deleteSession(tabId)

    fun getActiveSession(): Session = chatSessionStorage.getSession(activeCodeTestTabId)

    private fun isInvalidSession() = chatSessionStorage.getSession(activeCodeTestTabId).isAuthenticating

    // helper for adding a brand new answer(card) to chat UI.
    suspend fun addAnswer(
        content: CodeTestChatMessageContent,
        messageIdOverride: String? = null,
    ): String? {
        if (isInvalidSession()) return null

        val messageId = messageIdOverride ?: UUID.randomUUID().toString()
        messagePublisher.publish(
            CodeTestAddAnswerMessage(
                tabId = activeCodeTestTabId,
                messageId = messageId,
                messageType = content.type,
                message = content.message,
                buttons = content.buttons,
                formItems = content.formItems,
                followUps = content.followUps,
                canBeVoted = content.canBeVoted,
                isAddingNewItem = true,
                isLoading = content.type == ChatMessageType.AnswerStream,
                clearPreviousItemButtons = false,
                fileList = content.fileList,
                footer = content.footer,
                projectRootName = content.projectRootName,
                codeReference = content.codeReference
            )
        )
        return messageId
    }

    // helper for updating a specific chat card to chat UI. If messageId is not specified, update the last card.
    suspend fun updateAnswer(
        content: CodeTestChatMessageContent,
        messageIdOverride: String? = null,
    ) {
        if (isInvalidSession()) return

        messagePublisher.publish(
            CodeTestUpdateAnswerMessage(
                tabId = activeCodeTestTabId,
                messageId = messageIdOverride,
                messageType = content.type,
                message = content.message,
                buttons = content.buttons,
                formItems = content.formItems,
                followUps = content.followUps,
                isAddingNewItem = true,
                isLoading = content.type == ChatMessageType.AnswerPart,
                clearPreviousItemButtons = false,
                fileList = content.fileList,
                footer = content.footer,
                projectRootName = content.projectRootName,
                codeReference = content.codeReference
            )
        )
    }

    suspend fun sendUpdatePlaceholder(tabId: String, newPlaceholder: String) {
        messagePublisher.publish(
            UpdatePlaceholderMessage(
                tabId = tabId,
                newPlaceholder = newPlaceholder,
            )
        )
    }

    // Everything to be nullable so that only those that are assigned are changed
    suspend fun updateUI(
        loadingChat: Boolean? = null,
        cancelButtonWhenLoading: Boolean? = null,
        promptInputPlaceholder: String? = null,
        promptInputDisabledState: Boolean? = null,
        promptInputProgress: ProgressField? = null,
    ) {
        messagePublisher.publish(
            CodeTestUpdateUIMessage(
                activeCodeTestTabId,
                loadingChat,
                cancelButtonWhenLoading,
                promptInputPlaceholder,
                promptInputDisabledState,
                promptInputProgress
            )
        )
    }

    // currently only used for removing progress bar
    suspend fun sendUpdatePromptProgress(tabId: String, progressField: ProgressField?) {
        if (isInvalidSession()) return
        messagePublisher.publish(PromptProgressMessage(tabId, progressField))
    }

    suspend fun addNewMessage(
        content: CodeTestChatMessageContent,
        messageIdOverride: String? = null,
        clearPreviousItemButtons: Boolean? = false,
    ) {
        if (isInvalidSession()) return

        messagePublisher.publish(
            CodeTestChatMessage(
                tabId = activeCodeTestTabId,
                messageId = messageIdOverride ?: UUID.randomUUID().toString(),
                messageType = content.type,
                message = content.message,
                buttons = content.buttons,
                formItems = content.formItems,
                followUps = content.followUps,
                canBeVoted = content.canBeVoted,
                informationCard = content.informationCard,
                isAddingNewItem = true,
                isLoading = content.type == ChatMessageType.AnswerPart,
                clearPreviousItemButtons = clearPreviousItemButtons as Boolean
            )
        )
    }

    suspend fun sendChatInputEnabledMessage(isEnabled: Boolean) {
        if (isInvalidSession()) return
        messagePublisher.publish(ChatInputEnabledMessage(activeCodeTestTabId as String, enabled = isEnabled))
    }

    suspend fun sendAuthenticationInProgressMessage(
        tabId: String,
        messageId: String? = null,
        followUp: List<FollowUp>? = null,
        canBeVoted: Boolean? = false,
    ) {
        val chatMessage =
            CodeTestChatMessage(
                tabId = tabId,
                messageId = messageId ?: UUID.randomUUID().toString(),
                messageType = ChatMessageType.Answer,
                message = message("amazonqFeatureDev.follow_instructions_for_authentication"),
                followUps = followUp,
                canBeVoted = canBeVoted ?: false,
            )
        messagePublisher.publish(chatMessage)
    }
}
