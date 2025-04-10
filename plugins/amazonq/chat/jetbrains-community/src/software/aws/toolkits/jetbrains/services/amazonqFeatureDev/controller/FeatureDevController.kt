// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.util.selectFolder
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileSelectedListener
import software.aws.toolkits.jetbrains.services.amazonq.project.RepoSizeError
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindowFactory
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeIterationLimitException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.DEFAULT_RETRY_LIMIT
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FeatureDevException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.GENERATE_DEV_FILE_PROMPT
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.InboundAppMessagesHandler
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ModifySourceFolderErrorReason
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MonthlyConversationLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.NoChangeRequiredException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.UploadURLExpired
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ZipFileCorruptedException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.createUserFacingErrorMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.denyListedErrors
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FeatureDevMessageType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpIcons
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.IncomingFeatureDevMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.initialExamples
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswer
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAuthNeededException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAuthenticationInProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendMonthlyLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendSystemPrompt
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.updateFileComponent
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.PrepareCodeGenerationState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Session
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.InsertAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.getFollowUpOptions
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.QFeatureEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.broadcastQEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.util.content
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.FeedbackComment
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.ui.feedback.FeatureDevFeedbackDialog
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.Result
import software.aws.toolkits.telemetry.UiTelemetry
import java.util.UUID

class FeatureDevController(
    private val context: AmazonQAppInitContext,
    private val chatSessionStorage: ChatSessionStorage,
    private val authController: AuthController = AuthController(),
) : InboundAppMessagesHandler {

    init {
        context.project.messageBus.connect().subscribe(
            QRegionProfileSelectedListener.TOPIC,
            object : QRegionProfileSelectedListener {
                override fun onProfileSelected(project: Project, profile: QRegionProfile?) {
                    chatSessionStorage.deleteAllSessions()
                }
            }
        )
    }

    val messenger = context.messagesFromAppToUi
    val toolWindow = ToolWindowManager.getInstance(context.project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID)

    private val diffVirtualFiles = mutableMapOf<String, ChainDiffVirtualFile>()

    override suspend fun processPromptChatMessage(message: IncomingFeatureDevMessage.ChatPrompt) {
        handleChat(
            tabId = message.tabId,
            message = message.chatMessage
        )
    }

    override suspend fun processStoreCodeResultMessageId(message: IncomingFeatureDevMessage.StoreMessageIdMessage) {
        storeCodeResultMessageId(message)
    }

    override suspend fun processStopMessage(message: IncomingFeatureDevMessage.StopResponse) {
        handleStopMessage(message)
    }

    override suspend fun processNewTabCreatedMessage(message: IncomingFeatureDevMessage.NewTabCreated) {
        newTabOpened(message.tabId)
    }

    override suspend fun processTabRemovedMessage(message: IncomingFeatureDevMessage.TabRemoved) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processAuthFollowUpClick(message: IncomingFeatureDevMessage.AuthFollowUpWasClicked) {
        authController.handleAuth(context.project, message.authType)
        messenger.sendAuthenticationInProgressMessage(message.tabId) // show user that authentication is in progress
        messenger.sendChatInputEnabledMessage(message.tabId, enabled = false) // disable the input field while authentication is in progress
    }

    override suspend fun processFollowupClickedMessage(message: IncomingFeatureDevMessage.FollowupClicked) {
        when (message.followUp.type) {
            FollowUpTypes.RETRY -> retryRequests(message.tabId)
            FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER -> modifyDefaultSourceFolder(message.tabId)
            FollowUpTypes.DEV_EXAMPLES -> messenger.initialExamples(message.tabId)
            FollowUpTypes.SEND_FEEDBACK -> sendFeedback()
            FollowUpTypes.INSERT_CODE -> insertCode(message.tabId)
            FollowUpTypes.PROVIDE_FEEDBACK_AND_REGENERATE_CODE -> provideFeedbackAndRegenerateCode(message.tabId)
            FollowUpTypes.NEW_TASK -> newTask(message.tabId)
            FollowUpTypes.CLOSE_SESSION -> closeSession(message.tabId)
            FollowUpTypes.ACCEPT_AUTO_BUILD -> handleDevCommandUserSetting(message.tabId, true)
            FollowUpTypes.DENY_AUTO_BUILD -> handleDevCommandUserSetting(message.tabId, false)
            FollowUpTypes.GENERATE_DEV_FILE -> {
                messenger.sendAnswer(
                    tabId = message.tabId,
                    messageType = FeatureDevMessageType.SystemPrompt,
                    message = message("amazonqFeatureDev.follow_up.generate_dev_file")
                )
                newTask(tabId = message.tabId, prefilledPrompt = GENERATE_DEV_FILE_PROMPT)
            }
        }
    }

    override suspend fun processChatItemVotedMessage(message: IncomingFeatureDevMessage.ChatItemVotedMessage) {
        logger.debug { "$FEATURE_NAME: Processing ChatItemVotedMessage: $message" }
        this.disablePreviousFileList(message.tabId)

        val session = chatSessionStorage.getSession(message.tabId, context.project)
        when (message.vote) {
            "upvote" -> {
                AmazonqTelemetry.codeGenerationThumbsUp(
                    amazonqConversationId = session.conversationId,
                    credentialStartUrl = getStartUrl(project = context.project)
                )
            }
            "downvote" -> {
                AmazonqTelemetry.codeGenerationThumbsDown(
                    amazonqConversationId = session.conversationId,
                    credentialStartUrl = getStartUrl(project = context.project)
                )
            }
        }
    }

    override suspend fun processChatItemFeedbackMessage(message: IncomingFeatureDevMessage.ChatItemFeedbackMessage) {
        logger.debug { "$FEATURE_NAME: Processing ChatItemFeedbackMessage: ${message.comment}" }

        val session = getSessionInfo(message.tabId)

        val comment = FeedbackComment(
            conversationId = session.conversationId,
            userComment = message.comment.orEmpty(),
            reason = message.selectedOption,
            messageId = message.messageId,
            type = "featuredev-chat-answer-feedback"
        )

        try {
            TelemetryService.getInstance().sendFeedback(
                sentiment = Sentiment.NEGATIVE,
                comment = objectMapper.writeValueAsString(comment),
            )
            logger.info { "$FEATURE_NAME answer feedback sent: \"Negative\"" }
        } catch (e: Throwable) {
            e.notifyError(message("feedback.submit_failed", e))
            logger.warn(e) { "Failed to submit feedback" }
            return
        }
    }

    override suspend fun processLinkClick(message: IncomingFeatureDevMessage.ClickedLink) {
        BrowserUtil.browse(message.link)
    }

    override suspend fun processInsertCodeAtCursorPosition(message: IncomingFeatureDevMessage.InsertCodeAtCursorPosition) {
        logger.debug { "$FEATURE_NAME: Processing InsertCodeAtCursorPosition: $message" }

        withContext(EDT) {
            broadcastQEvent(QFeatureEvent.STARTS_EDITING)
            val editor: Editor = FileEditorManager.getInstance(context.project).selectedTextEditor ?: return@withContext

            val caret: Caret = editor.caretModel.primaryCaret
            val offset: Int = caret.offset

            WriteCommandAction.runWriteCommandAction(context.project) {
                if (caret.hasSelection()) {
                    editor.document.deleteString(caret.selectionStart, caret.selectionEnd)
                }
                editor.document.insertString(offset, message.code)
            }
            broadcastQEvent(QFeatureEvent.FINISHES_EDITING)
        }
    }

    private fun putDiff(filePath: String, request: SimpleDiffRequest) {
        // Close any existing diff and open a new diff, as the diff virtual file does not appear to allow replacing content directly:
        val existingDiff = diffVirtualFiles[filePath]
        if (existingDiff != null) {
            FileEditorManager.getInstance(context.project).closeFile(existingDiff)
        }

        val newDiff = ChainDiffVirtualFile(SimpleDiffRequestChain(request), filePath)
        DiffEditorTabFilesManager.getInstance(context.project).showDiffFile(newDiff, true)
        diffVirtualFiles[filePath] = newDiff
    }

    override suspend fun processOpenDiff(message: IncomingFeatureDevMessage.OpenDiff) {
        val session = getSessionInfo(message.tabId)

        AmazonqTelemetry.isReviewedChanges(
            amazonqConversationId = session.conversationId,
            enabled = true,
            credentialStartUrl = getStartUrl(project = context.project)
        )

        val project = context.project
        val sessionState = session.sessionState

        when (sessionState) {
            is PrepareCodeGenerationState -> {
                runInEdt {
                    val existingFile = VfsUtil.findRelativeFile(message.filePath, session.context.addressableRoot)

                    val leftDiffContent = if (existingFile == null) {
                        EmptyContent()
                    } else {
                        DiffContentFactory.getInstance().create(project, existingFile)
                    }

                    val newFileContent = sessionState.filePaths.find { it.zipFilePath == message.filePath }?.fileContent

                    val rightDiffContent = if (message.deleted || newFileContent == null) {
                        EmptyContent()
                    } else {
                        DiffContentFactory.getInstance().create(newFileContent)
                    }

                    putDiff(message.filePath, SimpleDiffRequest(message.filePath, leftDiffContent, rightDiffContent, null, null))
                }
            }
            else -> {
                logger.error { "$FEATURE_NAME: OpenDiff event is received for a conversation that has ${session.sessionState.phase} phase" }
                messenger.sendError(
                    tabId = message.tabId,
                    errMessage = message("amazonqFeatureDev.exception.open_diff_failed"),
                    retries = 0,
                    conversationId = session.conversationIdUnsafe
                )
            }
        }
    }

    override suspend fun processFileClicked(message: IncomingFeatureDevMessage.FileClicked) {
        val fileToUpdate = message.filePath
        val session = getSessionInfo(message.tabId)
        val messageId = message.messageId
        val action = message.actionName

        var filePaths: List<NewFileZipInfo> = emptyList()
        var deletedFiles: List<DeletedFileInfo> = emptyList()
        var references: List<CodeReferenceGenerated> = emptyList()
        when (val state = session.sessionState) {
            is PrepareCodeGenerationState -> {
                filePaths = state.filePaths
                deletedFiles = state.deletedFiles
                references = state.references
            }
        }

        fun insertAction(): InsertAction =
            if (filePaths.all { it.changeApplied } && deletedFiles.all { it.changeApplied }) {
                InsertAction.AUTO_CONTINUE
            } else if (filePaths.all { it.changeApplied || it.rejected } && deletedFiles.all { it.changeApplied || it.rejected }) {
                InsertAction.CONTINUE
            } else if (filePaths.any { it.changeApplied || it.rejected } || deletedFiles.any { it.changeApplied || it.rejected }) {
                InsertAction.REMAINING
            } else {
                InsertAction.ALL
            }

        val prevInsertAction = insertAction()

        if (action == "accept-change") {
            session.insertChanges(
                filePaths = filePaths.filter { it.zipFilePath == fileToUpdate },
                deletedFiles = deletedFiles.filter { it.zipFilePath == fileToUpdate },
                references = references, // Add all references (not attributed per-file)
            )

            AmazonqTelemetry.isAcceptedCodeChanges(
                amazonqNumberOfFilesAccepted = 1.0,
                amazonqConversationId = session.conversationId,
                enabled = true,
                credentialStartUrl = getStartUrl(project = context.project)
            )
        } else {
            // Mark the file as rejected or not depending on the previous state
            filePaths.find { it.zipFilePath == fileToUpdate }?.let { it.rejected = !it.rejected }
            deletedFiles.find { it.zipFilePath == fileToUpdate }?.let { it.rejected = !it.rejected }
        }

        messenger.updateFileComponent(message.tabId, filePaths, deletedFiles, messageId)

        // Then, if the accepted file is not a deletion, open a diff to show the changes are applied:
        if (action == "accept-change" && deletedFiles.none { it.zipFilePath == fileToUpdate }) {
            var pollAttempt = 0
            val pollDelayMs = 10L
            while (pollAttempt < 5) {
                val file = VfsUtil.findRelativeFile(message.filePath, session.context.addressableRoot)
                // Wait for the file to be created and/or updated to the new content:
                if (file != null && file.content() == filePaths.find { it.zipFilePath == fileToUpdate }?.fileContent) {
                    // Open a diff, showing the changes have been applied and the file now has identical left/right state:
                    this.processOpenDiff(IncomingFeatureDevMessage.OpenDiff(message.tabId, fileToUpdate, false))
                    break
                } else {
                    pollAttempt++
                    delay(pollDelayMs)
                }
            }
        }

        val nextInsertAction = insertAction()
        if (nextInsertAction == InsertAction.AUTO_CONTINUE) {
            // Insert remaining changes (noop, as there are none), and advance to the next prompt:
            insertCode(message.tabId)
        } else if (nextInsertAction != prevInsertAction) {
            // Update the action displayed to the customer based on the current state:
            messenger.sendSystemPrompt(message.tabId, getFollowUpOptions(session.sessionState.phase, nextInsertAction))
        }
    }

    private suspend fun newTabOpened(tabId: String) {
        var session: Session? = null
        try {
            session = getSessionInfo(tabId)
            logger.debug { "$FEATURE_NAME: Session created with id: ${session.tabID}" }

            val credentialState = authController.getAuthNeededStates(context.project).amazonQ
            if (credentialState != null) {
                messenger.sendAuthNeededException(
                    tabId = tabId,
                    triggerId = UUID.randomUUID().toString(),
                    credentialState = credentialState,
                )
                session.isAuthenticating = true
                return
            }
        } catch (err: Exception) {
            val message = createUserFacingErrorMessage(err.message)
            messenger.sendError(
                tabId = tabId,
                errMessage = message ?: message("amazonqFeatureDev.exception.request_failed"),
                retries = retriesRemaining(session),
                conversationId = session?.conversationIdUnsafe
            )
        }
    }

    private suspend fun handleStopMessage(message: IncomingFeatureDevMessage.StopResponse) {
        val session: Session?
        UiTelemetry.click(null as Project?, "amazonq_stopCodeGeneration")
        messenger.sendAnswer(
            tabId = message.tabId,
            message("amazonqFeatureDev.code_generation.stopping_code_generation"),
            messageType = FeatureDevMessageType.Answer,
            canBeVoted = false
        )
        messenger.sendUpdatePlaceholder(
            tabId = message.tabId,
            newPlaceholder = message("amazonqFeatureDev.code_generation.stopping_code_generation")
        )
        messenger.sendChatInputEnabledMessage(tabId = message.tabId, enabled = false)
        session = getSessionInfo(message.tabId)

        if (session.sessionState.token?.token !== null) {
            session.sessionState.token?.cancel()
        }
    }

    suspend fun insertCode(tabId: String) {
        var session: Session? = null
        try {
            session = getSessionInfo(tabId)

            var filePaths: List<NewFileZipInfo> = emptyList()
            var deletedFiles: List<DeletedFileInfo> = emptyList()
            var references: List<CodeReferenceGenerated> = emptyList()

            when (val state = session.sessionState) {
                is PrepareCodeGenerationState -> {
                    filePaths = state.filePaths
                    deletedFiles = state.deletedFiles
                    references = state.references
                }
            }

            val rejectedFilesCount = filePaths.count { it.rejected } + deletedFiles.count { it.rejected }
            val acceptedFilesCount = filePaths.count { it.changeApplied } + filePaths.count { it.changeApplied }
            val remainingFilesCount = filePaths.count() + deletedFiles.count() - acceptedFilesCount - rejectedFilesCount

            AmazonqTelemetry.isAcceptedCodeChanges(
                amazonqNumberOfFilesAccepted = remainingFilesCount.toDouble(),
                amazonqConversationId = session.conversationId,
                enabled = true,
                credentialStartUrl = getStartUrl(project = context.project)
            )

            session.insertChanges(
                filePaths = filePaths,
                deletedFiles = deletedFiles,
                references = references
            )
            session.updateFilesPaths(
                filePaths = filePaths,
                deletedFiles = deletedFiles,
                messenger
            )

            messenger.sendAnswer(
                tabId = tabId,
                message = message("amazonqFeatureDev.code_generation.updated_code"),
                messageType = FeatureDevMessageType.Answer,
                canBeVoted = true
            )

            val followUps = mutableListOf(
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.new_task"),
                    type = FollowUpTypes.NEW_TASK,
                    status = FollowUpStatusType.Info
                ),
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.close_session"),
                    type = FollowUpTypes.CLOSE_SESSION,
                    status = FollowUpStatusType.Info
                ),
            )

            if (!session.context.checkForDevFile()) {
                followUps.add(
                    FollowUp(
                        pillText = message("amazonqFeatureDev.follow_up.generate_dev_file"),
                        type = FollowUpTypes.GENERATE_DEV_FILE,
                        status = FollowUpStatusType.Info
                    )
                )

                messenger.sendAnswer(
                    tabId = tabId,
                    message = message("amazonqFeatureDev.chat_message.generate_dev_file"),
                    messageType = FeatureDevMessageType.Answer
                )
            }

            messenger.sendSystemPrompt(
                tabId = tabId,
                followUp = followUps
            )

            messenger.sendUpdatePlaceholder(
                tabId = tabId,
                newPlaceholder = message("amazonqFeatureDev.placeholder.additional_improvements")
            )
        } catch (err: Exception) {
            val message = createUserFacingErrorMessage("Failed to insert code changes: ${err.message}")
            messenger.sendError(
                tabId = tabId,
                errMessage = message ?: message("amazonqFeatureDev.exception.insert_code_failed"),
                retries = retriesRemaining(session),
                conversationId = session?.conversationIdUnsafe
            )
        }
    }

    private suspend fun newTask(tabId: String, isException: Boolean? = false, prefilledPrompt: String? = null) {
        val session = getSessionInfo(tabId)
        val sessionLatency = System.currentTimeMillis() - session.sessionStartTime

        AmazonqTelemetry.endChat(
            amazonqConversationId = session.conversationId,
            amazonqEndOfTheConversationLatency = sessionLatency.toDouble(),
            credentialStartUrl = getStartUrl(project = context.project)
        )
        chatSessionStorage.deleteSession(tabId)

        newTabOpened(tabId)

        if (prefilledPrompt != null && isException != null && !isException) {
            handleChat(tabId = tabId, message = prefilledPrompt)
        } else {
            if (isException != null && !isException) {
                messenger.sendAnswer(
                    tabId = tabId,
                    messageType = FeatureDevMessageType.Answer,
                    message = message("amazonqFeatureDev.chat_message.ask_for_new_task")
                )
            }
            messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.new_plan"))
            messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = true)
        }
    }

    private suspend fun handleDevCommandUserSetting(tabId: String, value: Boolean) {
        CodeWhispererSettings.getInstance().toggleAutoBuildFeature(context.project.basePath, value)
        messenger.sendAnswer(
            tabId = tabId,
            message = message("amazonqFeatureDev.chat_message.setting_updated"),
            messageType = FeatureDevMessageType.Answer,
        )
        this.retryRequests(tabId)
    }

    private suspend fun closeSession(tabId: String) {
        this.disablePreviousFileList(tabId)
        messenger.sendAnswer(
            tabId = tabId,
            messageType = FeatureDevMessageType.Answer,
            message = message("amazonqFeatureDev.chat_message.closed_session"),
            canBeVoted = true
        )

        messenger.sendUpdatePlaceholder(
            tabId = tabId,
            newPlaceholder = message("amazonqFeatureDev.placeholder.closed_session")
        )

        messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false)

        val session = getSessionInfo(tabId)
        val sessionLatency = System.currentTimeMillis() - session.sessionStartTime
        AmazonqTelemetry.endChat(
            amazonqConversationId = session.conversationId,
            amazonqEndOfTheConversationLatency = sessionLatency.toDouble(),
            credentialStartUrl = getStartUrl(project = context.project)
        )
    }

    private suspend fun provideFeedbackAndRegenerateCode(tabId: String) {
        val session = getSessionInfo(tabId)

        AmazonqTelemetry.isProvideFeedbackForCodeGen(
            amazonqConversationId = session.conversationId,
            enabled = true,
            credentialStartUrl = getStartUrl(project = context.project)
        )

        // Unblock the message button
        messenger.sendAsyncEventProgress(tabId = tabId, inProgress = false)

        messenger.sendAnswer(
            tabId = tabId,
            message = message("amazonqFeatureDev.code_generation.provide_code_feedback"),
            messageType = FeatureDevMessageType.Answer,
            canBeVoted = true
        )
        messenger.sendUpdatePlaceholder(tabId, message("amazonqFeatureDev.placeholder.provide_code_feedback"))
    }

    private suspend fun processErrorChatMessage(err: Exception, message: String, session: Session?, tabId: String) {
        logger.warn(err) { "Encountered ${err.message} for tabId: $tabId" }
        when (err) {
            is RepoSizeError -> {
                messenger.sendError(
                    tabId = tabId,
                    errMessage = err.message,
                    retries = retriesRemaining(session),
                    conversationId = session?.conversationIdUnsafe
                )
                messenger.sendSystemPrompt(
                    tabId = tabId,
                    followUp = listOf(
                        FollowUp(
                            pillText = message("amazonqFeatureDev.follow_up.modify_source_folder"),
                            type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER,
                            status = FollowUpStatusType.Info,
                        )
                    ),
                )
            }
            is NoChangeRequiredException -> {
                val isException = true
                messenger.sendAnswer(
                    tabId = tabId,
                    message = err.message,
                    messageType = FeatureDevMessageType.Answer,
                    canBeVoted = true
                )
                return this.newTask(message, isException)
            }
            is ZipFileCorruptedException -> {
                messenger.sendError(
                    tabId = tabId,
                    errMessage = err.message,
                    retries = 0,
                    conversationId = session?.conversationIdUnsafe
                )
            }
            is MonthlyConversationLimitError -> {
                messenger.sendMonthlyLimitError(tabId = tabId)
                messenger.sendChatInputEnabledMessage(tabId, enabled = false)
            }
            is UploadURLExpired -> messenger.sendAnswer(
                tabId = tabId,
                message = err.message,
                messageType = FeatureDevMessageType.Answer,
                canBeVoted = true
            )
            is CodeIterationLimitException -> {
                messenger.sendError(
                    tabId = tabId,
                    errMessage = err.message,
                    retries = retriesRemaining(session),
                    conversationId = session?.conversationIdUnsafe
                )
                messenger.sendSystemPrompt(
                    tabId = tabId,
                    followUp = listOf(
                        FollowUp(
                            pillText = message("amazonqFeatureDev.follow_up.insert_all_code"),
                            type = FollowUpTypes.INSERT_CODE,
                            icon = FollowUpIcons.Ok,
                            status = FollowUpStatusType.Success,
                        )
                    ),
                )
            }
            else -> {
                when (err) {
                    is FeatureDevException -> {
                        messenger.sendError(
                            tabId = tabId,
                            errMessage = err.message,
                            retries = retriesRemaining(session),
                            conversationId = session?.conversationIdUnsafe
                        )
                    }
                    else -> {
                        val msg = createUserFacingErrorMessage("$FEATURE_NAME request failed: ${err.message ?: err.cause?.message}")
                        val isDenyListedError = denyListedErrors.any { msg?.contains(it) ?: false }
                        val defaultMessage: String = when (session?.sessionState?.phase) {
                            SessionStatePhase.CODEGEN -> {
                                if (isDenyListedError || retriesRemaining(session) > 0) {
                                    message("amazonqFeatureDev.code_generation.error_message")
                                } else {
                                    message("amazonqFeatureDev.code_generation.no_retries.error_message")
                                }
                            }
                            else -> message("amazonqFeatureDev.error_text")
                        }
                        messenger.sendError(
                            tabId = tabId,
                            errMessage = defaultMessage,
                            retries = retriesRemaining(session),
                            conversationId = session?.conversationIdUnsafe
                        )
                    }
                }
            }
        }
    }

    private suspend fun disablePreviousFileList(tabId: String) {
        val session = getSessionInfo(tabId)
        when (val sessionState = session.sessionState) {
            is PrepareCodeGenerationState -> {
                session.disableFileList(sessionState.filePaths, sessionState.deletedFiles, messenger)
            }
        }
    }

    private fun storeCodeResultMessageId(message: IncomingFeatureDevMessage.StoreMessageIdMessage) {
        val tabId = message.tabId
        val session = getSessionInfo(tabId)
        session.storeCodeResultMessageId(message)
    }

    private suspend fun handleChat(
        tabId: String,
        message: String,
    ) {
        var session: Session? = null

        this.disablePreviousFileList(tabId)
        try {
            logger.debug { "$FEATURE_NAME: Processing message: $message" }
            session = getSessionInfo(tabId)
            session.latestMessage = message

            val credentialState = authController.getAuthNeededStates(context.project).amazonQ
            if (credentialState != null) {
                messenger.sendAuthNeededException(
                    tabId = tabId,
                    triggerId = UUID.randomUUID().toString(),
                    credentialState = credentialState,
                )
                session.isAuthenticating = true
                return
            }

            val codeWhispererSettings = CodeWhispererSettings.getInstance().getAutoBuildSetting()
            val hasDevFile = session.context.checkForDevFile()
            val isPromptedForAutoBuildFeature = codeWhispererSettings.containsKey(session.context.workspaceRoot.path)

            if (hasDevFile && !isPromptedForAutoBuildFeature) {
                promptAllowQCommandsConsent(messenger, tabId)
                return
            }

            session.preloader(messenger)
            broadcastQEvent(QFeatureEvent.INVOCATION)

            when (session.sessionState.phase) {
                SessionStatePhase.CODEGEN -> onCodeGeneration(session, message, tabId)
                else -> null
            }
        } catch (err: Exception) {
            processErrorChatMessage(err, message, session, tabId)

            // Lock the chat input until they explicitly click one of the follow-ups
            messenger.sendChatInputEnabledMessage(tabId, enabled = false)
        }
    }

    private suspend fun promptAllowQCommandsConsent(messenger: MessagePublisher, tabID: String) {
        messenger.sendAnswer(
            tabId = tabID,
            message = message("amazonqFeatureDev.chat_message.devFileInRepository"),
            messageType = FeatureDevMessageType.Answer
        )
        messenger.sendAnswer(
            tabId = tabID,
            messageType = FeatureDevMessageType.SystemPrompt,
            followUp = listOf(
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.accept_for_project"),
                    type = FollowUpTypes.ACCEPT_AUTO_BUILD,
                    status = FollowUpStatusType.Success
                ),
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.decline_for_project"),
                    type = FollowUpTypes.DENY_AUTO_BUILD,
                    status = FollowUpStatusType.Error
                )
            )
        )
    }

    private suspend fun retryRequests(tabId: String) {
        var session: Session? = null
        try {
            messenger.sendAsyncEventProgress(
                tabId = tabId,
                inProgress = true,
            )
            session = getSessionInfo(tabId)

            // Decrease retries before making this request, just in case this one fails as well
            session.decreaseRetries()

            // Sending an empty message will re-run the last state with the previous values
            handleChat(
                tabId = tabId,
                message = session.latestMessage
            )
        } catch (err: Exception) {
            logger.error(err) { "Failed to retry request: ${err.message}" }
            val message = createUserFacingErrorMessage("Failed to retry request: ${err.message}")
            messenger.sendError(
                tabId = tabId,
                errMessage = message ?: message("amazonqFeatureDev.exception.retry_request_failed"),
                retries = retriesRemaining(session),
                conversationId = session?.conversationIdUnsafe,
            )
        } finally {
            // Finish processing the event
            messenger.sendAsyncEventProgress(
                tabId = tabId,
                inProgress = false,
            )
        }
    }

    private suspend fun modifyDefaultSourceFolder(tabId: String) {
        val session = getSessionInfo(tabId)
        val workspaceRoot = session.context.workspaceRoot

        val modifyFolderFollowUp = FollowUp(
            pillText = message("amazonqFeatureDev.follow_up.modify_source_folder"),
            type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER,
            status = FollowUpStatusType.Info,
        )

        var result: Result = Result.Failed
        var reason: ModifySourceFolderErrorReason? = null

        withContext(EDT) {
            val selectedFolder = selectFolder(context.project, workspaceRoot)
            // No folder was selected
            if (selectedFolder == null) {
                logger.info { "Cancelled dialog and not selected any folder" }

                messenger.sendSystemPrompt(
                    tabId = tabId,
                    followUp = listOf(modifyFolderFollowUp),
                )

                reason = ModifySourceFolderErrorReason.ClosedBeforeSelection
                return@withContext
            }

            if (!selectedFolder.path.startsWith(workspaceRoot.path)) {
                logger.info { "Selected folder not in workspace: ${selectedFolder.path}" }

                messenger.sendAnswer(
                    tabId = tabId,
                    messageType = FeatureDevMessageType.Answer,
                    message = message("amazonqFeatureDev.follow_up.incorrect_source_folder"),
                )

                messenger.sendSystemPrompt(
                    tabId = tabId,
                    followUp = listOf(modifyFolderFollowUp),
                )

                reason = ModifySourceFolderErrorReason.NotInWorkspaceFolder
                return@withContext
            }

            logger.info { "Selected correct folder inside workspace: ${selectedFolder.path}" }

            session.context.selectionRoot = selectedFolder
            result = Result.Succeeded

            messenger.sendAnswer(
                tabId = tabId,
                messageType = FeatureDevMessageType.Answer,
                message = message("amazonqFeatureDev.follow_up.modified_source_folder", selectedFolder.path),
                canBeVoted = true,
            )

            messenger.sendAnswer(
                tabId = tabId,
                messageType = FeatureDevMessageType.SystemPrompt,
                followUp = listOf(
                    FollowUp(
                        pillText = message("amazonqFeatureDev.follow_up.retry"),
                        type = FollowUpTypes.RETRY,
                        status = FollowUpStatusType.Warning
                    )
                ),
            )

            messenger.sendChatInputEnabledMessage(tabId, enabled = false)
            messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.write_new_prompt"))
        }

        AmazonqTelemetry.modifySourceFolder(
            amazonqConversationId = session.conversationId,
            credentialStartUrl = getStartUrl(project = context.project),
            result = result,
            reason = reason?.toString()
        )
    }

    private fun sendFeedback() {
        runInEdt {
            FeatureDevFeedbackDialog(context.project).show()
        }
    }

    fun getProject() = context.project

    private fun getSessionInfo(tabId: String) = chatSessionStorage.getSession(tabId, context.project)

    fun retriesRemaining(session: Session?): Int = session?.retries ?: DEFAULT_RETRY_LIMIT

    companion object {
        private val logger = getLogger<FeatureDevController>()

        private val objectMapper = jacksonObjectMapper()
    }
}
