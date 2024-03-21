// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer.controller

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.codemodernizer.ArtifactHandler
import software.aws.toolkits.jetbrains.services.codemodernizer.CodeModernizerManager
import software.aws.toolkits.jetbrains.services.codemodernizer.InboundAppMessagesHandler
import software.aws.toolkits.jetbrains.services.codemodernizer.client.GumbyClient
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformActionMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.commands.CodeTransformCommand
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCheckingValidProjectChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalFailedChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalInProgressChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildCompileLocalSuccessChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildProjectInvalidChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildProjectValidChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformInProgressChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformResultChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformResumingChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformStoppedChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformStoppingChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserCancelledChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserInputChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserSelectionSummaryChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildUserStopTransformChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.getModuleOrProjectNameForFile
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.AuthenticationNeededExceptionMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformChatMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformCommandMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.IncomingCodeTransformMessage
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CustomerSelection
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.MavenCopyCommandsResult
import software.aws.toolkits.jetbrains.services.codemodernizer.session.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.codemodernizer.session.Session
import software.aws.toolkits.jetbrains.services.codemodernizer.state.CodeModernizerSessionState
import software.aws.toolkits.jetbrains.services.codemodernizer.toVirtualFile
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeTransformStartSrcComponents

class CodeTransformChatController(
    private val context: AmazonQAppInitContext,
    private val chatSessionStorage: ChatSessionStorage
) : InboundAppMessagesHandler {
    private val authController = AuthController()
    private val messagePublisher = context.messagesFromAppToUi
    private val codeModernizerManager = CodeModernizerManager.getInstance(context.project)
    private val codeTransformChatHelper = CodeTransformChatHelper(context.messagesFromAppToUi)
    private val artifactHandler = ArtifactHandler(context.project, GumbyClient.getInstance(context.project))

    override suspend fun processTransformQuickAction(message: IncomingCodeTransformMessage.Transform) {
        if (!checkForAuth(message.tabId)) {
            return
        }

        codeModernizerManager.sendUserClickedTelemetry(CodeTransformStartSrcComponents.ChatPrompt)
        codeTransformChatHelper.setActiveCodeTransformTabId(message.tabId)

        if (message.startNewTransform) {
            codeTransformChatHelper.clearHistory()
        }

        val isTransformOngoing = codeModernizerManager.isModernizationJobActive()
        val isMvnRunning = codeModernizerManager.isRunningMvn()
        val isTransformationResuming = codeModernizerManager.isModernizationJobResuming()

        while (isTransformationResuming) {
            delay(50)
        }

        if (isTransformOngoing || isMvnRunning || !codeTransformChatHelper.isHistoryEmpty()) {
            // Resume history
            runBlocking {
                codeTransformChatHelper.restoreCurrentChatHistory()
            }
            return
        }

        codeTransformChatHelper.addNewMessage(
            buildCheckingValidProjectChatContent()
        )

        delay(3000)

        val validationResult = codeModernizerManager.validate(context.project)

        if (!validationResult.valid) {
            codeTransformChatHelper.updateLastPendingMessage(
                buildProjectInvalidChatContent(validationResult)
            )
            codeModernizerManager.warnUnsupportedProject(validationResult.invalidReason)

            return
        }

        codeTransformChatHelper.updateLastPendingMessage(
            buildProjectValidChatContent(validationResult)
        )

        delay(500)

        codeTransformChatHelper.addNewMessage(
            buildUserInputChatContent(context.project, validationResult)
        )
    }

    override suspend fun processCodeTransformCancelAction(message: IncomingCodeTransformMessage.CodeTransformCancel) {
        codeTransformChatHelper.run {
            removeLastHistoryItem()

            addNewMessage(buildUserCancelledChatContent())
        }
    }

    override suspend fun processCodeTransformStartAction(message: IncomingCodeTransformMessage.CodeTransformStart) {
        val (tabId, modulePath, targetVersion) = message
        val moduleVirtualFile: VirtualFile = modulePath.toVirtualFile() as VirtualFile
        val moduleName = context.project.getModuleOrProjectNameForFile(moduleVirtualFile)

        codeTransformChatHelper.run {
            removeLastHistoryItem()

            addNewMessage(buildUserSelectionSummaryChatContent(moduleName))

            addNewMessage(buildCompileLocalInProgressChatContent())
        }

        val selection = CustomerSelection(
            moduleVirtualFile,
            JavaSdkVersion.JDK_1_8,
            JavaSdkVersion.JDK_17
        )
        val session = codeModernizerManager.createCodeModernizerSession(selection, context.project)

        val result = codeModernizerManager.getDependenciesUsingMaven(session)
        if (result == MavenCopyCommandsResult.Cancelled) {
            codeTransformChatHelper.updateLastPendingMessage(buildUserCancelledChatContent())

            return
        } else if (result == MavenCopyCommandsResult.Failure) {
            codeTransformChatHelper.updateLastPendingMessage(buildCompileLocalFailedChatContent())

            return
        }

        codeTransformChatHelper.run {
            updateLastPendingMessage(buildCompileLocalSuccessChatContent())

            addNewMessage(buildTransformInProgressChatContent())
        }

        runInEdt {
            codeModernizerManager.runModernize(session, result)
        }
    }

    override suspend fun processCodeTransformStopAction(message: IncomingCodeTransformMessage.CodeTransformStop) {
        codeTransformChatHelper.run {
            addNewMessage(buildUserStopTransformChatContent())

            addNewMessage(buildTransformStoppingChatContent())
        }

        runBlocking {
            codeModernizerManager.stopModernize()
        }
    }

    override suspend fun processCodeTransformOpenTransformHub(message: IncomingCodeTransformMessage.CodeTransformOpenTransformHub) {
        runInEdt {
            codeModernizerManager.getBottomToolWindow().show()
        }
    }

    override suspend fun processCodeTransformOpenMvnBuild(message: IncomingCodeTransformMessage.CodeTransformOpenMvnBuild) {
        runInEdt {
            codeModernizerManager.getMvnBuildWindow().show()
        }
    }

    override suspend fun processCodeTransformViewDiff(message: IncomingCodeTransformMessage.CodeTransformViewDiff) {
        artifactHandler.displayDiffAction(CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId)
    }

    override suspend fun processCodeTransformViewSummary(message: IncomingCodeTransformMessage.CodeTransformViewSummary) {
        artifactHandler.showTransformationSummary(CodeModernizerSessionState.getInstance(context.project).currentJobId as JobId)
    }

    override suspend fun processCodeTransformNewAction(message: IncomingCodeTransformMessage.CodeTransformNew) {
        codeTransformChatHelper.clearHistory()

        processTransformQuickAction(IncomingCodeTransformMessage.Transform(tabId = message.tabId, startNewTransform = true))
    }

    override suspend fun processCodeTransformCommand(message: CodeTransformActionMessage) {
        if (message.command == CodeTransformCommand.Start) {
            messagePublisher.publish(CodeTransformCommandMessage(command = "start"))
        } else if (message.command == CodeTransformCommand.Stop) {
            messagePublisher.publish(CodeTransformCommandMessage(command = "stop"))
        } else if (message.command == CodeTransformCommand.TransformComplete) {
            val result = message.result
            if (result != null) {
                handleCodeTransformResult(result)
            }
        } else if (message.command == CodeTransformCommand.Cancel) {
            handleCodeTransformStoppedByUser()
        } else if (message.command == CodeTransformCommand.TransformResuming) {
            handleCodeTransformJobResume()
        }
    }

    override suspend fun processTabCreated(message: IncomingCodeTransformMessage.TabCreated) {
        logger.debug("$FEATURE_NAME: New tab created: $message")
    }

    private suspend fun checkForAuth(tabId: String): Boolean {
        var session: Session? = null
        try {
            session = chatSessionStorage.getSession(tabId)
            logger.debug { "$FEATURE_NAME: Session created with id: ${session.tabId}" }

            val credentialState = authController.getAuthNeededStates(context.project).amazonQ
            if (credentialState != null) {
                messagePublisher.publish(
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
            messagePublisher.publish(
                CodeTransformChatMessage(
                    tabId = tabId,
                    messageType = ChatMessageType.Answer,
                    message = message("codemodernizer.chat.message.error_request")
                )
            )
            return false
        }

        return true
    }

    override suspend fun processTabRemoved(message: IncomingCodeTransformMessage.TabRemoved) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processAuthFollowUpClick(message: IncomingCodeTransformMessage.AuthFollowUpWasClicked) {
        authController.handleAuth(context.project, message.authType)
        messagePublisher.publish(
            CodeTransformChatMessage(
                tabId = message.tabId,
                messageType = ChatMessageType.Answer,
                message = message("codemodernizer.chat.message.auth_prompt")
            )
        )
    }

    override suspend fun processBodyLinkClicked(message: IncomingCodeTransformMessage.BodyLinkClicked) {
        BrowserUtil.browse(message.link)
    }

    private suspend fun handleCodeTransformJobResume() {
        codeTransformChatHelper.addNewMessage(buildTransformResumingChatContent())
    }

    private suspend fun handleCodeTransformStoppedByUser() {
        codeTransformChatHelper.updateLastPendingMessage(buildTransformStoppedChatContent())
    }

    private suspend fun handleCodeTransformResult(result: CodeModernizerJobCompletedResult) {
        val resultMessage = when (result) {
            is CodeModernizerJobCompletedResult.JobAbortedZipTooLarge -> {
                message("codemodernizer.chat.message.result.zip_too_large")
            }
            is CodeModernizerJobCompletedResult.JobCompletedSuccessfully -> {
                message("codemodernizer.chat.message.result.success")
            }
            is CodeModernizerJobCompletedResult.JobPartiallySucceeded -> {
                message("codemodernizer.chat.message.result.partially_success")
            }
            else -> {
                message("codemodernizer.chat.message.result.fail")
            }
        }

        codeTransformChatHelper.updateLastPendingMessage(
            buildTransformResultChatContent(result)
        )
    }

    companion object {
        private val logger = getLogger<CodeTransformChatController>()
    }
}
