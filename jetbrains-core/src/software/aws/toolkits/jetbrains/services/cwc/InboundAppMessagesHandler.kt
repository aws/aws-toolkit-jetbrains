// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc

import software.aws.toolkits.jetbrains.services.amazonq.onboarding.OnboardingPageInteraction
import software.aws.toolkits.jetbrains.services.cwc.commands.ContextMenuActionMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.IncomingCwcMessage

/*
(TODO): Messages to listen to (from vscode)
    processTriggerTabIDReceived
 */
interface InboundAppMessagesHandler {
    suspend fun processPromptChatMessage(message: IncomingCwcMessage.ChatPrompt, testTriggerId: String? = null)
    suspend fun processTabWasRemoved(message: IncomingCwcMessage.TabRemoved)
    suspend fun processTabChanged(message: IncomingCwcMessage.TabChanged)
    suspend fun processFollowUpClick(message: IncomingCwcMessage.FollowupClicked, testTriggerId: String? = null)
    suspend fun processCodeWasCopiedToClipboard(message: IncomingCwcMessage.CopyCodeToClipboard)
    suspend fun processInsertCodeAtCursorPosition(message: IncomingCwcMessage.InsertCodeAtCursorPosition)
    suspend fun processStopResponseMessage(message: IncomingCwcMessage.StopResponse)
    suspend fun processChatItemVoted(message: IncomingCwcMessage.ChatItemVoted)
    suspend fun processChatItemFeedback(message: IncomingCwcMessage.ChatItemFeedback)
    suspend fun processUIFocus(message: IncomingCwcMessage.UIFocus)
    suspend fun processAuthFollowUpClick(message: IncomingCwcMessage.AuthFollowUpWasClicked)
    suspend fun processOnboardingPageInteraction(message: OnboardingPageInteraction)

    // JB specific (not in vscode)
    suspend fun processClearQuickAction(message: IncomingCwcMessage.ClearChat)
    suspend fun processHelpQuickAction(message: IncomingCwcMessage.Help, testTriggerId: String? = null)
    suspend fun processTransformQuickAction(message: IncomingCwcMessage.Transform, testTriggerId: String? = null)
    suspend fun processContextMenuCommand(message: ContextMenuActionMessage, testTriggerId: String? = null)

    suspend fun processLinkClick(message: IncomingCwcMessage.ClickedLink)
}
