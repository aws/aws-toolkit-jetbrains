// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeScan.controller

import com.intellij.ide.BrowserUtil
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.InboundAppMessagesHandler
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildHelpChatAnswerContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildHelpChatPromptContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildNotInGitRepoChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildProjectScanFailedChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildScanCompleteChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildScanInProgressChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildStartNewScanChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildUserSelectionFileScanChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.buildUserSelectionProjectScanChatContent
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands.CodeScanActionMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.commands.CodeScanCommand
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.AuthenticationNeededExceptionMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.CodeScanChatMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.messages.IncomingCodeScanMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeScan.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeScanResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getAuthType
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.resources.message

class CodeScanChatController(
    private val context: AmazonQAppInitContext,
    private val chatSessionStorage: ChatSessionStorage,
    private val authController: AuthController = AuthController(),
) : InboundAppMessagesHandler {

    private val messenger = context.messagesFromAppToUi
    private val codeScanManager = CodeWhispererCodeScanManager.getInstance(context.project)
    private val codeScanChatHelper = CodeScanChatHelper(context.messagesFromAppToUi, chatSessionStorage)
    private val scanInProgressMessageId = "scanProgressMessage"

    override suspend fun processScanQuickAction(message: IncomingCodeScanMessage.Scan) {
        // TODO: telemetry

        if (!checkForAuth(message.tabId)) {
            return
        }
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return

        codeScanChatHelper.setActiveCodeScanTabId(message.tabId)
        codeScanChatHelper.addNewMessage(buildStartNewScanChatContent())
        codeScanChatHelper.sendChatInputEnabledMessage(false)
    }

    override suspend fun processStartProjectScan(message: IncomingCodeScanMessage.StartProjectScan) {
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return
        codeScanChatHelper.addNewMessage(buildUserSelectionProjectScanChatContent())
        if (!codeScanManager.isInsideWorkTree()) {
            codeScanChatHelper.addNewMessage(buildNotInGitRepoChatContent())
        }
        codeScanChatHelper.addNewMessage(buildScanInProgressChatContent(currentStep = 1, isProject = true), messageIdOverride = scanInProgressMessageId)
        codeScanManager.runCodeScan(CodeWhispererConstants.CodeAnalysisScope.PROJECT, initiatedByChat = true)
        codeScanChatHelper.updateProgress(isProject = true, isCanceling = false)
    }

    override suspend fun processStopProjectScan(message: IncomingCodeScanMessage.StopProjectScan) {
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return
        codeScanChatHelper.updateProgress(isProject = true, isCanceling = true)
        codeScanManager.stopCodeScan(CodeWhispererConstants.CodeAnalysisScope.PROJECT)
    }

    override suspend fun processStopFileScan(message: IncomingCodeScanMessage.StopFileScan) {
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return
        codeScanChatHelper.updateProgress(isProject = false, isCanceling = true)
        codeScanManager.stopCodeScan(CodeWhispererConstants.CodeAnalysisScope.FILE)
    }

    override suspend fun processStartFileScan(message: IncomingCodeScanMessage.StartFileScan) {
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return
        codeScanChatHelper.addNewMessage(buildUserSelectionFileScanChatContent())
        codeScanChatHelper.addNewMessage(buildScanInProgressChatContent(currentStep = 1, isProject = false), messageIdOverride = scanInProgressMessageId)
        codeScanManager.runCodeScan(CodeWhispererConstants.CodeAnalysisScope.FILE, initiatedByChat = true)
        codeScanChatHelper.updateProgress(isProject = false, isCanceling = false)
    }

    override suspend fun processTabCreated(message: IncomingCodeScanMessage.TabCreated) {
        logger.debug { "$FEATURE_NAME: New tab created: $message" }
        codeScanChatHelper.setActiveCodeScanTabId(message.tabId)
        CodeWhispererTelemetryService.getInstance().sendCodeScanNewTabEvent(getAuthType(context.project))
    }

    override suspend fun processClearQuickAction(message: IncomingCodeScanMessage.ClearChat) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processHelpQuickAction(message: IncomingCodeScanMessage.Help) {
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return
        codeScanChatHelper.addNewMessage(buildHelpChatPromptContent())
        codeScanChatHelper.addNewMessage(buildHelpChatAnswerContent())
    }

    override suspend fun processTabRemoved(message: IncomingCodeScanMessage.TabRemoved) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processResponseBodyLinkClicked(message: IncomingCodeScanMessage.ResponseBodyLinkClicked) {
        BrowserUtil.browse(message.link)
    }

    override suspend fun processCodeScanCommand(message: CodeScanActionMessage) {
        if (message.project != context.project) return
        val isProject = message.scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT
        when (message.command) {
            CodeScanCommand.ScanComplete -> {
                codeScanChatHelper.addNewMessage(
                    buildScanInProgressChatContent(currentStep = 2, isProject = isProject),
                    messageIdOverride = scanInProgressMessageId
                )
                val result = message.scanResult
                if (result != null) {
                    handleCodeScanResult(result, message.scope)
                } else {
                    codeScanChatHelper.addNewMessage(buildProjectScanFailedChatContent("Cancelled"))
                    codeScanChatHelper.clearProgress()
                }
            }
        }
    }

    private suspend fun handleCodeScanResult(result: CodeScanResponse, scope: CodeWhispererConstants.CodeAnalysisScope) {
        val isProject = scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT
        when (result) {
            is CodeScanResponse.Success -> {
                codeScanChatHelper.addNewMessage(
                    buildScanInProgressChatContent(currentStep = 3, isProject = isProject),
                    messageIdOverride = scanInProgressMessageId
                )
                codeScanChatHelper.addNewMessage(buildScanCompleteChatContent(result.issues, isProject = isProject))
                codeScanChatHelper.clearProgress()
            }
            is CodeScanResponse.Failure -> {
                codeScanChatHelper.addNewMessage(buildScanInProgressChatContent(3, isProject = isProject), messageIdOverride = scanInProgressMessageId)
                codeScanChatHelper.addNewMessage(buildProjectScanFailedChatContent(result.failureReason.message))
                codeScanChatHelper.clearProgress()
            }
        }
    }

    /**
     * Return true if authenticated, else show authentication message and return false
     * // TODO: Refactor this to avoid code duplication with other controllers
     */
    private suspend fun checkForAuth(tabId: String): Boolean {
        try {
            val session = chatSessionStorage.getSession(tabId)
            logger.debug { "$FEATURE_NAME: Session created with id: ${session.tabId}" }

            val credentialState = authController.getAuthNeededStates(context.project).amazonQ
            if (credentialState != null) {
                messenger.publish(
                    AuthenticationNeededExceptionMessage(
                        tabId = session.tabId,
                        authType = credentialState.authType,
                        message = credentialState.message
                    )
                )
                session.isAuthenticating = true
                return false
            }
        } catch (err: Exception) {
            messenger.publish(
                CodeScanChatMessage(
                    tabId = tabId,
                    messageType = ChatMessageType.Answer,
                    message = message("codescan.chat.message.error_request")
                )
            )
            return false
        }

        return true
    }

    override suspend fun processOpenIssuesPanel(message: IncomingCodeScanMessage.OpenIssuesPanel) {
        if (message.tabId != codeScanChatHelper.getActiveCodeScanTabId()) return
        codeScanManager.showCodeScanUI()
    }

    companion object {
        private val logger = getLogger<CodeScanChatController>()
    }
}
