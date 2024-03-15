// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.controller

import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessageContent
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessageType
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformNotificationMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType

class CodeTransformChatHelper(
    private val messagePublisher: MessagePublisher
) {
    private var activeCodeTransformTabId: String? = null
    private val messageHistory = mutableListOf<CodeTransformChatMessageContent>()
    fun setActiveCodeTransformTabId(tabId: String) {
        activeCodeTransformTabId = tabId
    }

    suspend fun showChatNotification(title:  String, content: String) {
        messagePublisher.publish(
            CodeTransformNotificationMessage(
                title = title,
                content = content,
            )
        )
    }

    suspend fun addNewMessage(content: CodeTransformChatMessageContent) {
        // If the previous item is in loading state
        if (messageHistory.isNotEmpty() &&  messageHistory.last().type == CodeTransformChatMessageType.PendingAnswer) {
            messageHistory[messageHistory.lastIndex] = messageHistory.last().copy(
                type = CodeTransformChatMessageType.FinalizedAnswer,
                // Remove the buttons and follow ups
                buttons = listOf(),
                formItems = listOf(),
            )
            if (activeCodeTransformTabId != null) {
                messagePublisher.publish(
                    CodeTransformChatMessage(
                        tabId = activeCodeTransformTabId as String,
                        messageType = ChatMessageType.AnswerPart,
                        message = messageHistory.last().message,
                        buttons = messageHistory.last().buttons,
                        formItems = messageHistory.last().formItems,
                    )
                )
            }
        }
        messageHistory.add(content)

        if (activeCodeTransformTabId != null) {
            // Send a answer stream first to show the loading state
            if (content.type == CodeTransformChatMessageType.PendingAnswer) {
                messagePublisher.publish(
                    CodeTransformChatMessage(
                        tabId = activeCodeTransformTabId as String,
                        messageType = ChatMessageType.AnswerStream,
                        message = "",
                    )
                )
            }

            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = activeCodeTransformTabId as String,
                    messageType = when (content.type) {
                        CodeTransformChatMessageType.PendingAnswer -> ChatMessageType.AnswerPart
                        CodeTransformChatMessageType.FinalizedAnswer -> ChatMessageType.Answer
                        CodeTransformChatMessageType.Prompt -> ChatMessageType.Prompt
                    },
                    message = content.message,
                    buttons = content.buttons,
                    formItems = content.formItems,
                    followUps = content.followUps,
                )
            )
        }
    }

    suspend fun updateLastPendingMessage(content: CodeTransformChatMessageContent) {
        if (messageHistory.isEmpty() || messageHistory.last().type != CodeTransformChatMessageType.PendingAnswer) {
            addNewMessage(content)
            return
        }

        messageHistory[messageHistory.lastIndex] = content.copy()

        if (content.type == CodeTransformChatMessageType.FinalizedAnswer) {
            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = activeCodeTransformTabId as String,
                    messageType = ChatMessageType.AnswerPart,
                    message = content.message,
                    buttons = content.buttons,
                    formItems = content.formItems,
                )
            )

            // Send follow up as a separate message to stop the loading state
            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = activeCodeTransformTabId as String,
                    messageType = ChatMessageType.Answer,
                    followUps = content.followUps ?: listOf()
                )
            )
        } else {
            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = activeCodeTransformTabId as String,
                    messageType = ChatMessageType.AnswerPart,
                    message = content.message,
                    buttons = content.buttons,
                    formItems = content.formItems,
                    followUps = content.followUps,
                )
            )
        }
    }

    fun removeLastHistoryItem() {
        if (messageHistory.isNotEmpty()) {
            messageHistory.removeLast()
        }
    }

    fun clearHistory() {
        messageHistory.clear()
    }

    fun isHistoryEmpty(): Boolean = messageHistory.isEmpty()

    suspend fun restoreCurrentChatHistory() {
        for (codeTransformChatMessageContent in messageHistory) {
            if (codeTransformChatMessageContent.type == CodeTransformChatMessageType.PendingAnswer) {
                messagePublisher.publish(
                    CodeTransformChatMessage(
                        tabId = activeCodeTransformTabId as String,
                        messageType = ChatMessageType.AnswerStream,
                        message = "",
                    )
                )
            }

            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = activeCodeTransformTabId as String,
                    messageType = when (codeTransformChatMessageContent.type) {
                        CodeTransformChatMessageType.Prompt -> ChatMessageType.Prompt
                        CodeTransformChatMessageType.FinalizedAnswer -> ChatMessageType.Answer
                        CodeTransformChatMessageType.PendingAnswer -> ChatMessageType.AnswerPart
                    },
                    message = codeTransformChatMessageContent.message,
                    buttons = codeTransformChatMessageContent.buttons,
                    formItems = codeTransformChatMessageContent.formItems,
                    followUps = codeTransformChatMessageContent.followUps,
                )
            )
        }
    }
}
