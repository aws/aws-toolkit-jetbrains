// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQApp
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.auth.isCodeScanAvailable
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.auth.isCodeTestAvailable
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller.CodeTestChatController
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.AuthenticationUpdateMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.IncomingCodeTestMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.amazonqDoc.auth.isDocAvailable
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.auth.isFeatureDevAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable

class CodeTestChatApp(private val scope: CoroutineScope) : AmazonQApp {

    override val tabTypes = listOf("codetest")

    override fun init(context: AmazonQAppInitContext) {
        val chatSessionStorage = ChatSessionStorage()
        val inboundAppMessagesHandler =
            CodeTestChatController(context, chatSessionStorage, cs = scope)

        context.messageTypeRegistry.register(
            "clear" to IncomingCodeTestMessage.ClearChat::class,
            "help" to IncomingCodeTestMessage.Help::class,
            "chat-prompt" to IncomingCodeTestMessage.ChatPrompt::class,
            "new-tab-was-created" to IncomingCodeTestMessage.NewTabCreated::class,
            "tab-was-removed" to IncomingCodeTestMessage.TabRemoved::class,
            "start-test-gen" to IncomingCodeTestMessage.StartTestGen::class,
            "response-body-link-click" to IncomingCodeTestMessage.ClickedLink::class,
            "button-click" to IncomingCodeTestMessage.ButtonClicked::class
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
                                authenticatingTabIDs = chatSessionStorage.getAuthenticatingSessions().map { it.tabId }
                            )
                        )
                    }
                }
            }
        )
    }

    private suspend fun handleMessage(message: AmazonQMessage, inboundAppMessagesHandler: InboundAppMessagesHandler) {
        when (message) {
            is IncomingCodeTestMessage.ClearChat -> inboundAppMessagesHandler.processClearQuickAction(message)
            is IncomingCodeTestMessage.Help -> inboundAppMessagesHandler.processHelpQuickAction(message)
            is IncomingCodeTestMessage.ChatPrompt -> inboundAppMessagesHandler.processPromptChatMessage(message)
            is IncomingCodeTestMessage.NewTabCreated -> inboundAppMessagesHandler.processNewTabCreatedMessage(message)
            is IncomingCodeTestMessage.TabRemoved -> inboundAppMessagesHandler.processTabRemovedMessage(message)
            is IncomingCodeTestMessage.StartTestGen -> inboundAppMessagesHandler.processStartTestGen(message)
            is IncomingCodeTestMessage.ClickedLink -> inboundAppMessagesHandler.processLinkClick(message)
            is IncomingCodeTestMessage.ButtonClicked -> inboundAppMessagesHandler.processButtonClickedMessage(message)
        }
    }

    override fun dispose() {
    }
}
