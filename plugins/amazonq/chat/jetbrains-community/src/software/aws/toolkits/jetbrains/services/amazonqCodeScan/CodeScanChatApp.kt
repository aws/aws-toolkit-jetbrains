// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQApp
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.auth.isCodeScanAvailable
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands.CodeScanActionMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands.CodeScanMessageListener
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.controller.CodeScanChatController
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.AuthenticationNeededExceptionMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.AuthenticationUpdateMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CODE_SCAN_TAB_NAME
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.IncomingCodeScanMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.isCodeTransformAvailable
import java.util.concurrent.atomic.AtomicBoolean

private enum class CodeScanMessageTypes(val type: String) {
    ClearChat("clear"),
    Help("help"),
    TabCreated("new-tab-was-created"),
    TabRemoved("tab-was-removed"),
    Scan("scan"),
    StartProjectScan("codescan_start_project_scan"),
    StartFileScan("codescan_start_file_scan"),
    StopFileScan("codescan_stop_file_scan"),
    StopProjectScan("codescan_stop_project_scan"),
    OpenIssuesPanel("codescan_open_issues"),
    ResponseBodyLinkClicked("response-body-link-click"),
}

class CodeScanChatApp(private val scope: CoroutineScope) : AmazonQApp {
    private val isProcessingAuthChanged = AtomicBoolean(false)
    override val tabTypes = listOf(CODE_SCAN_TAB_NAME)
    override fun init(context: AmazonQAppInitContext) {
        val chatSessionStorage = ChatSessionStorage()
        val inboundAppMessagesHandler: InboundAppMessagesHandler = CodeScanChatController(context, chatSessionStorage)

        context.messageTypeRegistry.register(
            CodeScanMessageTypes.ClearChat.type to IncomingCodeScanMessage.ClearChat::class,
            CodeScanMessageTypes.Help.type to IncomingCodeScanMessage.Help::class,
            CodeScanMessageTypes.TabCreated.type to IncomingCodeScanMessage.TabCreated::class,
            CodeScanMessageTypes.TabRemoved.type to IncomingCodeScanMessage.TabRemoved::class,
            CodeScanMessageTypes.Scan.type to IncomingCodeScanMessage.Scan::class,
            CodeScanMessageTypes.StartProjectScan.type to IncomingCodeScanMessage.StartProjectScan::class,
            CodeScanMessageTypes.StartFileScan.type to IncomingCodeScanMessage.StartFileScan::class,
            CodeScanMessageTypes.StopProjectScan.type to IncomingCodeScanMessage.StopProjectScan::class,
            CodeScanMessageTypes.StopFileScan.type to IncomingCodeScanMessage.StopFileScan::class,
            CodeScanMessageTypes.ResponseBodyLinkClicked.type to IncomingCodeScanMessage.ResponseBodyLinkClicked::class,
            CodeScanMessageTypes.OpenIssuesPanel.type to IncomingCodeScanMessage.OpenIssuesPanel::class
        )

        scope.launch {
            merge(service<CodeScanMessageListener>().flow, context.messagesFromUiToApp.flow).collect { message ->
                // Launch a new coroutine to handle each message
                scope.launch { handleMessage(message, inboundAppMessagesHandler) }
            }
        }

        fun authChanged() {
            val isAnotherThreadProcessing = !isProcessingAuthChanged.compareAndSet(false, true)
            if (isAnotherThreadProcessing) return
            scope.launch {
                val authController = AuthController()
                val credentialState = authController.getAuthNeededStates(context.project).amazonQ
                if (credentialState == null) {
                    // Notify tabs about restoring authentication
                    context.messagesFromAppToUi.publish(
                        AuthenticationUpdateMessage(
                            codeTransformEnabled = isCodeTransformAvailable(context.project),
                            codeScanEnabled = isCodeScanAvailable(context.project),
                            authenticatingTabIDs = chatSessionStorage.getAuthenticatingSessions().map { it.tabId }
                        )
                    )

                    chatSessionStorage.changeAuthenticationNeeded(false)
                    chatSessionStorage.changeAuthenticationNeededNotified(false)
                } else {
                    chatSessionStorage.changeAuthenticationNeeded(true)

                    // Ask for reauth
                    chatSessionStorage.getAuthenticatingSessions().filter { !it.authNeededNotified }.forEach {
                        context.messagesFromAppToUi.publish(
                            AuthenticationNeededExceptionMessage(
                                tabId = it.tabId,
                                authType = credentialState.authType,
                                message = credentialState.message
                            )
                        )
                    }

                    // Prevent multiple calls to activeConnectionChanged
                    chatSessionStorage.changeAuthenticationNeededNotified(true)
                }
                isProcessingAuthChanged.set(false)
            }
        }

        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            BearerTokenProviderListener.TOPIC,
            object : BearerTokenProviderListener {
                override fun onProviderChange(providerId: String, newScopes: List<String>?) {
                    val qProvider = getQTokenProvider(context.project)
                    val isQ = qProvider?.id == providerId
                    val isAuthorized = qProvider?.state() == BearerTokenAuthState.AUTHORIZED
                    if (!isQ || !isAuthorized) return
                    authChanged()
                }
            }
        )

        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    authChanged()
                }
            }
        )

        context.project.messageBus.connect(this).subscribe(
            QRegionProfileSelectedListener.TOPIC,
            object : QRegionProfileSelectedListener {
                override fun onProfileSelected(project: Project, profile: QRegionProfile?) {
                    chatSessionStorage.deleteAllSessions()
                }
            }
        )
    }

    private fun getQTokenProvider(project: Project) = (
        ToolkitConnectionManager
            .getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        )
        ?.getConnectionSettings()
        ?.tokenProvider
        ?.delegate as? BearerTokenProvider

    private suspend fun handleMessage(message: AmazonQMessage, inboundAppMessagesHandler: InboundAppMessagesHandler) {
        when (message) {
            is IncomingCodeScanMessage.ClearChat -> inboundAppMessagesHandler.processClearQuickAction(message)
            is IncomingCodeScanMessage.Help -> inboundAppMessagesHandler.processHelpQuickAction(message)
            is IncomingCodeScanMessage.TabCreated -> inboundAppMessagesHandler.processTabCreated(message)
            is IncomingCodeScanMessage.TabRemoved -> inboundAppMessagesHandler.processTabRemoved(message)
            is IncomingCodeScanMessage.Scan -> inboundAppMessagesHandler.processScanQuickAction(message)
            is IncomingCodeScanMessage.StartProjectScan -> inboundAppMessagesHandler.processStartProjectScan(message)
            is IncomingCodeScanMessage.StartFileScan -> inboundAppMessagesHandler.processStartFileScan(message)
            is CodeScanActionMessage -> inboundAppMessagesHandler.processCodeScanCommand(message)
            is IncomingCodeScanMessage.StopProjectScan -> inboundAppMessagesHandler.processStopProjectScan(message)
            is IncomingCodeScanMessage.StopFileScan -> inboundAppMessagesHandler.processStopFileScan(message)
            is IncomingCodeScanMessage.ResponseBodyLinkClicked -> inboundAppMessagesHandler.processResponseBodyLinkClicked(message)
            is IncomingCodeScanMessage.OpenIssuesPanel -> inboundAppMessagesHandler.processOpenIssuesPanel(message)
        }
    }

    override fun dispose() {
        // nothing to do
    }
}
