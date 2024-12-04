// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.IncomingDocMessage

interface InboundAppMessagesHandler {
    suspend fun processPromptChatMessage(message: IncomingDocMessage.ChatPrompt)
    suspend fun processNewTabCreatedMessage(message: IncomingDocMessage.NewTabCreated)
    suspend fun processTabRemovedMessage(message: IncomingDocMessage.TabRemoved)
    suspend fun processAuthFollowUpClick(message: IncomingDocMessage.AuthFollowUpWasClicked)
    suspend fun processFollowupClickedMessage(message: IncomingDocMessage.FollowupClicked)
    suspend fun processChatItemVotedMessage(message: IncomingDocMessage.ChatItemVotedMessage)
    suspend fun processChatItemFeedbackMessage(message: IncomingDocMessage.ChatItemFeedbackMessage)
    suspend fun processLinkClick(message: IncomingDocMessage.ClickedLink)
    suspend fun processInsertCodeAtCursorPosition(message: IncomingDocMessage.InsertCodeAtCursorPosition)
    suspend fun processOpenDiff(message: IncomingDocMessage.OpenDiff)
    suspend fun processFileClicked(message: IncomingDocMessage.FileClicked)
    suspend fun processStopDocGeneration(message: IncomingDocMessage.StopDocGeneration)
}
