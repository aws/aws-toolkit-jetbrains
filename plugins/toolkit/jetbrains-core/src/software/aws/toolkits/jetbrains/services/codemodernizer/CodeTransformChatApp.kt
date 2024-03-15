// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQApp
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.auth.isFeatureDevAvailable
import software.aws.toolkits.jetbrains.services.codemodernizer.session.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformActionMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformMessageListener
import software.aws.toolkits.jetbrains.services.codemodernizer.controller.CodeTransformChatController
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.AuthenticationUpdateMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.IncomingCodeTransformMessage

class CodeTransformChatApp : AmazonQApp {
    private val scope = disposableCoroutineScope(this)

    override val tabTypes = listOf("codetransform")

    override fun init(context: AmazonQAppInitContext) {
        val chatSessionStorage = ChatSessionStorage()
        val inboundAppMessagesHandler: InboundAppMessagesHandler = CodeTransformChatController(context, chatSessionStorage)

        context.messageTypeRegistry.register(
            "new-tab-was-created" to IncomingCodeTransformMessage.TabCreated::class,
            "tab-was-removed" to IncomingCodeTransformMessage.TabRemoved::class,
            "transform" to IncomingCodeTransformMessage.Transform::class,
            "codetransform-start" to IncomingCodeTransformMessage.CodeTransformStart::class,
            "codetransform-stop" to IncomingCodeTransformMessage.CodeTransformStop::class,
            "codetransform-cancel" to IncomingCodeTransformMessage.CodeTransformCancel::class,
            "codetransform-new" to IncomingCodeTransformMessage.CodeTransformNew::class,
            "codetransform-open-transform-hub" to IncomingCodeTransformMessage.CodeTransformOpenTransformHub::class,
            "codetransform-open-mvn-build" to IncomingCodeTransformMessage.CodeTransformOpenMvnBuild::class,
            "auth-follow-up-was-clicked" to IncomingCodeTransformMessage.AuthFollowUpWasClicked::class,
        )

        scope.launch {
            merge(CodeTransformMessageListener.instance.flow, context.messagesFromUiToApp.flow).collect { message ->
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
            is IncomingCodeTransformMessage.Transform -> inboundAppMessagesHandler.processTransformQuickAction(message)
            is IncomingCodeTransformMessage.CodeTransformStart -> inboundAppMessagesHandler.processCodeTransformStartAction(message)
            is IncomingCodeTransformMessage.CodeTransformCancel -> inboundAppMessagesHandler.processCodeTransformCancelAction(message)
            is IncomingCodeTransformMessage.CodeTransformStop -> inboundAppMessagesHandler.processCodeTransformStopAction(message)
            is IncomingCodeTransformMessage.CodeTransformNew -> inboundAppMessagesHandler.processCodeTransformNewAction(message)
            is IncomingCodeTransformMessage.CodeTransformOpenTransformHub -> inboundAppMessagesHandler.processCodeTransformOpenTransformHub(message)
            is IncomingCodeTransformMessage.CodeTransformOpenMvnBuild -> inboundAppMessagesHandler.processCodeTransformOpenMvnBuild(message)
            is IncomingCodeTransformMessage.TabCreated-> inboundAppMessagesHandler.processTabCreated(message)
            is IncomingCodeTransformMessage.TabRemoved-> inboundAppMessagesHandler.processTabRemoved(message)
            is IncomingCodeTransformMessage.AuthFollowUpWasClicked-> inboundAppMessagesHandler.processAuthFollowUpClick(message)
            is CodeTransformActionMessage -> inboundAppMessagesHandler.processCodeTransformCommand(message)
        }
    }

    override fun dispose() {
        // nothing to do
    }
}
