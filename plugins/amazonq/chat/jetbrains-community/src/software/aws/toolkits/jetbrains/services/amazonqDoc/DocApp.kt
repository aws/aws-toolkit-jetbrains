// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQApp
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.auth.isCodeScanAvailable
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.auth.isCodeTestAvailable
import software.aws.toolkits.jetbrains.services.amazonqDoc.auth.isDocAvailable
import software.aws.toolkits.jetbrains.services.amazonqDoc.controller.DocController
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.AuthenticationUpdateMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.IncomingDocMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.auth.isFeatureDevAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable

class DocApp : AmazonQApp {

    private val scope = disposableCoroutineScope(this)

    override val tabTypes = listOf("doc")

    override fun init(context: AmazonQAppInitContext) {
        val chatSessionStorage = ChatSessionStorage()
        // Create Doc controller
        val inboundAppMessagesHandler =
            DocController(context, chatSessionStorage)

        context.messageTypeRegistry.register(
            "chat-prompt" to IncomingDocMessage.ChatPrompt::class,
            "new-tab-was-created" to IncomingDocMessage.NewTabCreated::class,
            "tab-was-removed" to IncomingDocMessage.TabRemoved::class,
            "auth-follow-up-was-clicked" to IncomingDocMessage.AuthFollowUpWasClicked::class,
            "follow-up-was-clicked" to IncomingDocMessage.FollowupClicked::class,
            "chat-item-voted" to IncomingDocMessage.ChatItemVotedMessage::class,
            "chat-item-feedback" to IncomingDocMessage.ChatItemFeedbackMessage::class,
            "response-body-link-click" to IncomingDocMessage.ClickedLink::class,
            "insert_code_at_cursor_position" to IncomingDocMessage.InsertCodeAtCursorPosition::class,
            "open-diff" to IncomingDocMessage.OpenDiff::class,
            "file-click" to IncomingDocMessage.FileClicked::class,
            "doc_stop_generate" to IncomingDocMessage.StopDocGeneration::class
        )

        scope.launch {
            context.messagesFromUiToApp.flow.collect { message ->
                // Launch a new coroutine to handle each message
                scope.launch { handleMessage(message, inboundAppMessagesHandler) }
            }
        }

        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    scope.launch {
                        context.messagesFromAppToUi.publish(
                            AuthenticationUpdateMessage(
                                featureDevEnabled = isFeatureDevAvailable(context.project),
                                codeTransformEnabled = isCodeTransformAvailable(context.project),
                                codeScanEnabled = isCodeScanAvailable(context.project),
                                codeTestEnabled = isCodeTestAvailable(context.project),
                                docEnabled = isDocAvailable(context.project),
                                authenticatingTabIDs = chatSessionStorage.getAuthenticatingSessions().map { it.tabID }
                            )
                        )
                    }
                }
            }
        )

        context.project.messageBus.connect(this).subscribe(
            QRegionProfileSelectedListener.TOPIC,
            object : QRegionProfileSelectedListener {
                override fun onProfileSelected(profile: QRegionProfile?) {
                    chatSessionStorage.deleteAllSessions()
                }
            }
        )
    }

    private suspend fun handleMessage(message: AmazonQMessage, inboundAppMessagesHandler: InboundAppMessagesHandler) {
        when (message) {
            is IncomingDocMessage.ChatPrompt -> inboundAppMessagesHandler.processPromptChatMessage(message)
            is IncomingDocMessage.NewTabCreated -> inboundAppMessagesHandler.processNewTabCreatedMessage(message)
            is IncomingDocMessage.TabRemoved -> inboundAppMessagesHandler.processTabRemovedMessage(message)
            is IncomingDocMessage.AuthFollowUpWasClicked -> inboundAppMessagesHandler.processAuthFollowUpClick(message)
            is IncomingDocMessage.FollowupClicked -> inboundAppMessagesHandler.processFollowupClickedMessage(message)
            is IncomingDocMessage.ClickedLink -> inboundAppMessagesHandler.processLinkClick(message)
            is IncomingDocMessage.OpenDiff -> inboundAppMessagesHandler.processOpenDiff(message)
            is IncomingDocMessage.FileClicked -> inboundAppMessagesHandler.processFileClicked(message)
            is IncomingDocMessage.StopDocGeneration -> inboundAppMessagesHandler.processStopDocGeneration(message)
        }
    }

    override fun dispose() {
        // nothing to do
    }
}
