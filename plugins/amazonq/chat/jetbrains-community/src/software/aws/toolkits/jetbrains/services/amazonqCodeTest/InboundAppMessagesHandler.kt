// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest

import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.IncomingCodeTestMessage

interface InboundAppMessagesHandler {
    suspend fun processPromptChatMessage(message: IncomingCodeTestMessage.ChatPrompt)

    suspend fun processStartTestGen(message: IncomingCodeTestMessage.StartTestGen)

    suspend fun processLinkClick(message: IncomingCodeTestMessage.ClickedLink)

    suspend fun processNewTabCreatedMessage(message: IncomingCodeTestMessage.NewTabCreated)

    suspend fun processClearQuickAction(message: IncomingCodeTestMessage.ClearChat)

    suspend fun processHelpQuickAction(message: IncomingCodeTestMessage.Help)

    suspend fun processTabRemovedMessage(message: IncomingCodeTestMessage.TabRemoved)

    suspend fun processButtonClickedMessage(message: IncomingCodeTestMessage.ButtonClicked)

    suspend fun processChatItemVoted(message: IncomingCodeTestMessage.ChatItemVoted)

    suspend fun processChatItemFeedBack(message: IncomingCodeTestMessage.ChatItemFeedback)

    suspend fun processAuthFollowUpClick(message: IncomingCodeTestMessage.AuthFollowUpWasClicked)
}
