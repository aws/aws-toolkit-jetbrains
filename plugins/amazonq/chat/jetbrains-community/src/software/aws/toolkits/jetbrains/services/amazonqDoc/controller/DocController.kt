// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.controller

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.withContext
import software.amazon.awssdk.services.codewhispererruntime.model.DocFolderLevel
import software.amazon.awssdk.services.codewhispererruntime.model.DocInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.DocUserDecision
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.common.util.selectFolder
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.project.RepoSizeError
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindowFactory
import software.aws.toolkits.jetbrains.services.amazonqDoc.DEFAULT_RETRY_LIMIT
import software.aws.toolkits.jetbrains.services.amazonqDoc.DIAGRAM_SVG_EXT
import software.aws.toolkits.jetbrains.services.amazonqDoc.DocClientException
import software.aws.toolkits.jetbrains.services.amazonqDoc.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqDoc.InboundAppMessagesHandler
import software.aws.toolkits.jetbrains.services.amazonqDoc.MetricDataOperationName
import software.aws.toolkits.jetbrains.services.amazonqDoc.MetricDataResult
import software.aws.toolkits.jetbrains.services.amazonqDoc.createUserFacingErrorMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.denyListedErrors
import software.aws.toolkits.jetbrains.services.amazonqDoc.inProgress
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.DocMessageType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpIcons
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.IncomingDocMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.initialExamples
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAnswer
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAuthNeededException
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAuthenticationInProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendCodeResult
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendError
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendErrorToUser
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendFolderConfirmationMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendMonthlyLimitError
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendRetryChangeFolderMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendSystemPrompt
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.updateFileComponent
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.DocSession
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.PrepareDocGenerationState
import software.aws.toolkits.jetbrains.services.amazonqDoc.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.amazonqDoc.util.getFollowUpOptions
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeIterationLimitException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MonthlyConversationLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ZipFileCorruptedException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.resources.message
import java.util.UUID

enum class DocGenerationStep {
    UPLOAD_TO_S3,
    CREATE_KNOWLEDGE_GRAPH,
    SUMMARIZING_FILES,
    GENERATING_ARTIFACTS,
    COMPLETE,
}

enum class Mode(val value: String) {
    NONE("None"),
    CREATE("Create"),
    SYNC("Sync"),
    EDIT("Edit"),
}

val checkIcons = mapOf(
    "wait" to "&#9744;",
    "current" to "&#9744;",
    "done" to "&#9745;"
)

fun getIconForStep(targetStep: DocGenerationStep, currentStep: DocGenerationStep): String? = when {
    currentStep == targetStep -> checkIcons["current"]
    currentStep > targetStep -> checkIcons["done"]
    else -> checkIcons["wait"]
}

fun docGenerationProgressMessage(currentStep: DocGenerationStep, mode: Mode?): String {
    val isCreationMode = mode == Mode.CREATE
    val baseLine = if (isCreationMode) message("amazonqDoc.progress_message.creating") else message("amazonqDoc.progress_message.updating")

    return """
        $baseLine ${message("amazonqDoc.progress_message.baseline")}

        ${getIconForStep(DocGenerationStep.UPLOAD_TO_S3, currentStep)} ${message("amazonqDoc.progress_message.scanning")}

        ${getIconForStep(DocGenerationStep.SUMMARIZING_FILES, currentStep)} ${message("amazonqDoc.progress_message.summarizing")}

        ${getIconForStep(DocGenerationStep.GENERATING_ARTIFACTS, currentStep)} ${message("amazonqDoc.progress_message.generating")}
    """.trimIndent()
}

class DocController(
    private val context: AmazonQAppInitContext,
    private val chatSessionStorage: ChatSessionStorage,
    private val authController: AuthController = AuthController(),
) : InboundAppMessagesHandler {
    val messenger = context.messagesFromAppToUi
    val toolWindow = ToolWindowManager.getInstance(context.project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID)
    private val docGenerationTasks = DocGenerationTasks()

    override suspend fun processPromptChatMessage(message: IncomingDocMessage.ChatPrompt) {
        handleChat(
            tabId = message.tabId,
            message = message.chatMessage
        )
    }

    override suspend fun processNewTabCreatedMessage(message: IncomingDocMessage.NewTabCreated) {
        newTabOpened(message.tabId)
    }

    override suspend fun processTabRemovedMessage(message: IncomingDocMessage.TabRemoved) {
        docGenerationTasks.deleteTask(message.tabId)
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processAuthFollowUpClick(message: IncomingDocMessage.AuthFollowUpWasClicked) {
        authController.handleAuth(context.project, message.authType)
        messenger.sendAuthenticationInProgressMessage(message.tabId) // show user that authentication is in progress
        messenger.sendChatInputEnabledMessage(message.tabId, enabled = false) // disable the input field while authentication is in progress
    }

    override suspend fun processFollowupClickedMessage(message: IncomingDocMessage.FollowupClicked) {
        val session = getSessionInfo(message.tabId)
        val docGenerationTask = docGenerationTasks.getTask(message.tabId)

        session.preloader(message.followUp.pillText, messenger) // also stores message in session history

        when (message.followUp.type) {
            FollowUpTypes.RETRY -> retryRequests(message.tabId)
            FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER -> modifyDefaultSourceFolder(message.tabId)
            FollowUpTypes.DEV_EXAMPLES -> messenger.initialExamples(message.tabId)
            FollowUpTypes.INSERT_CODE -> insertCode(message.tabId)
            FollowUpTypes.PROVIDE_FEEDBACK_AND_REGENERATE_CODE -> provideFeedbackAndRegenerateCode(message.tabId)
            FollowUpTypes.NEW_TASK -> newTask(message.tabId)
            FollowUpTypes.CLOSE_SESSION -> closeSession(message.tabId)
            FollowUpTypes.CREATE_DOCUMENTATION -> {
                docGenerationTask.interactionType = DocInteractionType.GENERATE_README
                docGenerationTask.mode = Mode.CREATE
                promptForDocTarget(message.tabId)
            }

            FollowUpTypes.UPDATE_DOCUMENTATION -> {
                docGenerationTask.interactionType = DocInteractionType.UPDATE_README
                updateDocumentation(message.tabId)
            }

            FollowUpTypes.CANCEL_FOLDER_SELECTION -> {
                docGenerationTask.reset()
                newTask(message.tabId)
            }

            FollowUpTypes.PROCEED_FOLDER_SELECTION -> if (docGenerationTask.mode == Mode.EDIT) makeChanges(message.tabId) else onDocsGeneration(message)
            FollowUpTypes.ACCEPT_CHANGES -> {
                docGenerationTask.userDecision = DocUserDecision.ACCEPT
                sendDocAcceptanceTelemetry(message.tabId)
                acceptChanges(message)
            }

            FollowUpTypes.MAKE_CHANGES -> {
                docGenerationTask.mode = Mode.EDIT
                makeChanges(message.tabId)
            }

            FollowUpTypes.REJECT_CHANGES -> {
                docGenerationTask.userDecision = DocUserDecision.REJECT
                sendDocAcceptanceTelemetry(message.tabId)
                rejectChanges(message)
            }

            FollowUpTypes.SYNCHRONIZE_DOCUMENTATION -> {
                docGenerationTask.mode = Mode.SYNC
                promptForDocTarget(message.tabId)
            }

            FollowUpTypes.EDIT_DOCUMENTATION -> {
                docGenerationTask.mode = Mode.EDIT
                docGenerationTask.interactionType = DocInteractionType.EDIT_README
                promptForDocTarget(message.tabId)
            }
        }
    }

    override suspend fun processStopDocGeneration(message: IncomingDocMessage.StopDocGeneration) {
        messenger.sendAnswer(
            tabId = message.tabId,
            message("amazonqFeatureDev.code_generation.stopping_code_generation"),
            messageType = DocMessageType.Answer,
            canBeVoted = false
        )
        messenger.sendUpdatePlaceholder(
            tabId = message.tabId,
            newPlaceholder = message("amazonqFeatureDev.code_generation.stopping_code_generation")
        )
        messenger.sendChatInputEnabledMessage(tabId = message.tabId, enabled = false)
        val session = getSessionInfo(message.tabId)

        if (session.sessionState.token?.token !== null) {
            session.sessionState.token?.cancel()
        }

        newTask(message.tabId)
    }

    private suspend fun updateDocumentation(tabId: String) {
        messenger.sendAnswer(
            tabId,
            messageType = DocMessageType.Answer,
            followUp = listOf(
                FollowUp(
                    type = FollowUpTypes.SYNCHRONIZE_DOCUMENTATION,
                    pillText = message("amazonqDoc.prompt.update.follow_up.sync"),
                    prompt = message("amazonqDoc.prompt.update.follow_up.sync"),
                ),
                FollowUp(
                    type = FollowUpTypes.EDIT_DOCUMENTATION,
                    pillText = message("amazonqDoc.prompt.update.follow_up.edit"),
                    prompt = message("amazonqDoc.prompt.update.follow_up.edit"),
                )
            )
        )

        messenger.sendChatInputEnabledMessage(tabId, enabled = false)
    }

    private suspend fun makeChanges(tabId: String) {
        messenger.sendAnswer(
            tabId = tabId,
            message = message("amazonqDoc.edit.message"),
            messageType = DocMessageType.Answer
        )

        messenger.sendUpdatePlaceholder(tabId, message("amazonqDoc.edit.placeholder"))
        messenger.sendChatInputEnabledMessage(tabId, true)
    }

    private suspend fun rejectChanges(message: IncomingDocMessage.FollowupClicked) {
        messenger.sendAnswer(
            tabId = message.tabId,
            message = message("amazonqDoc.prompt.reject.message"),
            followUp = listOf(
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.new_task"),
                    prompt = message("amazonqFeatureDev.follow_up.new_task"),
                    status = FollowUpStatusType.Info,
                    type = FollowUpTypes.NEW_TASK
                ),
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.close_session"),
                    prompt = message("amazonqFeatureDev.follow_up.close_session"),
                    status = FollowUpStatusType.Info,
                    type = FollowUpTypes.CLOSE_SESSION
                )
            ),
            messageType = DocMessageType.Answer
        )

        messenger.sendChatInputEnabledMessage(message.tabId, false)
    }

    private val diffVirtualFiles = mutableMapOf<String, ChainDiffVirtualFile>()

    private suspend fun acceptChanges(message: IncomingDocMessage.FollowupClicked) {
        insertCode(message.tabId)
        previewReadmeFile(message.tabId)
    }

    private suspend fun promptForDocTarget(tabId: String) {
        val session = getSessionInfo(tabId)
        val docGenerationTask = docGenerationTasks.getTask(tabId)

        val currentSourceFolder = session.context.selectionRoot

        try {
            messenger.sendFolderConfirmationMessage(
                tabId = tabId,
                message = if (docGenerationTask.mode == Mode.CREATE) message("amazonqDoc.prompt.create.confirmation") else message("amazonqDoc.prompt.update"),
                folderPath = currentSourceFolder.name,
                followUps = listOf(
                    FollowUp(
                        icon = FollowUpIcons.Ok,
                        pillText = message("amazonqDoc.prompt.folder.proceed"),
                        prompt = message("amazonqDoc.prompt.folder.proceed"),
                        status = FollowUpStatusType.Success,
                        type = FollowUpTypes.PROCEED_FOLDER_SELECTION
                    ),
                    FollowUp(
                        icon = FollowUpIcons.Refresh,
                        pillText = message("amazonqDoc.prompt.folder.change"),
                        prompt = message("amazonqDoc.prompt.folder.change"),
                        status = FollowUpStatusType.Info,
                        type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER
                    ),
                    FollowUp(
                        icon = FollowUpIcons.Cancel,
                        pillText = message("general.cancel"),
                        prompt = message("general.cancel"),
                        status = FollowUpStatusType.Error,
                        type = FollowUpTypes.CANCEL_FOLDER_SELECTION
                    ),
                )
            )

            messenger.sendChatInputEnabledMessage(tabId, false)
        } catch (e: Exception) {
            logger.error { "Error sending answer: ${e.message}" }
            // Consider logging the error or handling it appropriately
        }
    }

    private suspend fun promptForRetryFolderSelection(tabId: String, message: String) {
        messenger.sendRetryChangeFolderMessage(
            tabId = tabId,
            message = message,
            followUps = listOf(
                FollowUp(
                    icon = FollowUpIcons.Refresh,
                    pillText = message("amazonqDoc.prompt.folder.change"),
                    prompt = message("amazonqDoc.prompt.folder.change"),
                    status = FollowUpStatusType.Info,
                    type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER
                ),
                FollowUp(
                    icon = FollowUpIcons.Cancel,
                    pillText = message("general.cancel"),
                    prompt = message("general.cancel"),
                    status = FollowUpStatusType.Error,
                    type = FollowUpTypes.CANCEL_FOLDER_SELECTION
                ),
            )
        )

        messenger.sendChatInputEnabledMessage(tabId, false)
    }

    override suspend fun processLinkClick(message: IncomingDocMessage.ClickedLink) {
        BrowserUtil.browse(message.link)
    }

    override suspend fun processOpenDiff(message: IncomingDocMessage.OpenDiff) {
        val session = getSessionInfo(message.tabId)
        val sessionState = session.sessionState

        if (sessionState !is PrepareDocGenerationState) {
            logger.error { "$FEATURE_NAME: OpenDiff event is received for a conversation that has ${session.sessionState.phase} phase" }
            messenger.sendError(
                tabId = message.tabId,
                errMessage = message("amazonqFeatureDev.exception.open_diff_failed"),
                retries = 0,
                conversationId = session.conversationIdUnsafe
            )
            return
        }

        runInEdt {
            val newFileContent = sessionState.filePaths.find { it.zipFilePath == message.filePath }?.fileContent

            val isSvgFile = message.filePath.lowercase().endsWith(".".plus(DIAGRAM_SVG_EXT))
            if (isSvgFile && newFileContent != null) {
                // instead of diff display generated svg in edit/preview window
                val inMemoryFile = LightVirtualFile(
                    message.filePath,
                    null,
                    newFileContent
                )
                inMemoryFile.isWritable = false
                FileEditorManager.getInstance(context.project).openFile(inMemoryFile, true)
            } else {
                val existingFile = VfsUtil.findRelativeFile(message.filePath, session.context.addressableRoot)
                val leftDiffContent = if (existingFile == null) {
                    EmptyContent()
                } else {
                    DiffContentFactory.getInstance().create(context.project, existingFile)
                }

                val rightDiffContent = if (message.deleted || newFileContent == null) {
                    EmptyContent()
                } else {
                    DiffContentFactory.getInstance().create(newFileContent)
                }

                val request = SimpleDiffRequest(message.filePath, leftDiffContent, rightDiffContent, null, null)
                request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)

                val newDiff = ChainDiffVirtualFile(SimpleDiffRequestChain(request), message.filePath)

                diffVirtualFiles[message.filePath] = newDiff
                DiffEditorTabFilesManager.getInstance(context.project).showDiffFile(newDiff, true)
            }
        }
    }

    override suspend fun processFileClicked(message: IncomingDocMessage.FileClicked) {
        val fileToUpdate = message.filePath
        val session = getSessionInfo(message.tabId)
        val messageId = message.messageId

        var filePaths: List<NewFileZipInfo> = emptyList()
        var deletedFiles: List<DeletedFileInfo> = emptyList()
        when (val state = session.sessionState) {
            is PrepareDocGenerationState -> {
                filePaths = state.filePaths
                deletedFiles = state.deletedFiles
            }
        }

        // Mark the file as rejected or not depending on the previous state
        filePaths.find { it.zipFilePath == fileToUpdate }?.let { it.rejected = !it.rejected }
        deletedFiles.find { it.zipFilePath == fileToUpdate }?.let { it.rejected = !it.rejected }

        messenger.updateFileComponent(message.tabId, filePaths, deletedFiles, messageId)
    }

    private suspend fun newTabOpened(tabId: String) {
        var session: DocSession? = null
        try {
            session = getSessionInfo(tabId)
            val docGenerationTask = docGenerationTasks.getTask(tabId)
            docGenerationTask.mode = Mode.NONE

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
            docGenerationTask.numberOfNavigations += 1
            messenger.sendUpdatePlaceholder(tabId, message("amazonqDoc.prompt.placeholder"))
        } catch (err: Exception) {
            val message = createUserFacingErrorMessage(err.message)
            messenger.sendErrorToUser(
                tabId = tabId,
                errMessage = message ?: message("amazonqFeatureDev.exception.request_failed"),
                conversationId = session?.conversationIdUnsafe,
            )
        }
    }

    private suspend fun insertCode(tabId: String) {
        var session: DocSession? = null
        try {
            session = getSessionInfo(tabId)

            var filePaths: List<NewFileZipInfo> = emptyList()
            var deletedFiles: List<DeletedFileInfo> = emptyList()

            when (val state = session.sessionState) {
                is PrepareDocGenerationState -> {
                    filePaths = state.filePaths
                    deletedFiles = state.deletedFiles
                }
            }

            session.insertChanges(
                filePaths = filePaths.filterNot { it.rejected },
                deletedFiles = deletedFiles.filterNot { it.rejected }
            )

            messenger.sendSystemPrompt(
                tabId = tabId,
                followUp = listOf(
                    FollowUp(
                        pillText = message("amazonqDoc.prompt.reject.new_task"),
                        type = FollowUpTypes.NEW_TASK,
                        status = FollowUpStatusType.Info
                    ),
                    FollowUp(
                        pillText = message("amazonqDoc.prompt.reject.close_session"),
                        type = FollowUpTypes.CLOSE_SESSION,
                        status = FollowUpStatusType.Info
                    )
                )
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

    private suspend fun newTask(tabId: String) {
        docGenerationTasks.deleteTask(tabId)
        chatSessionStorage.deleteSession(tabId)

        messenger.sendAnswer(
            tabId = tabId,
            messageType = DocMessageType.Answer,
            message = message("amazonqFeatureDev.chat_message.ask_for_new_task")
        )

        messenger.sendUpdatePlaceholder(
            tabId = tabId,
            newPlaceholder = message("amazonqFeatureDev.placeholder.after_code_generation")
        )

        newTabOpened(tabId)

        messenger.sendSystemPrompt(
            tabId = tabId,
            followUp = listOf(
                FollowUp(
                    pillText = message("amazonqDoc.prompt.create"),
                    prompt = message("amazonqDoc.prompt.create"),
                    type = FollowUpTypes.CREATE_DOCUMENTATION,
                ),
                FollowUp(
                    pillText = message("amazonqDoc.prompt.update"),
                    prompt = message("amazonqDoc.prompt.update"),
                    type = FollowUpTypes.UPDATE_DOCUMENTATION,
                )
            )
        )
    }

    private suspend fun closeSession(tabId: String) {
        messenger.sendAnswer(
            tabId = tabId,
            messageType = DocMessageType.Answer,
            message = message("amazonqFeatureDev.chat_message.closed_session"),
            canBeVoted = true
        )

        messenger.sendUpdatePlaceholder(
            tabId = tabId,
            newPlaceholder = message("amazonqFeatureDev.placeholder.closed_session")
        )

        messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false)
        docGenerationTasks.deleteTask(tabId)
    }

    private suspend fun provideFeedbackAndRegenerateCode(tabId: String) {
        // Unblock the message button
        messenger.sendAsyncEventProgress(tabId = tabId, inProgress = false)

        messenger.sendAnswer(
            tabId = tabId,
            message = message("amazonqFeatureDev.code_generation.provide_code_feedback"),
            messageType = DocMessageType.Answer,
            canBeVoted = true
        )
        messenger.sendUpdatePlaceholder(tabId, message("amazonqFeatureDev.placeholder.provide_code_feedback"))
    }

    private suspend fun processErrorChatMessage(err: Exception, session: DocSession?, tabId: String) {
        logger.warn(err) { "Encountered ${err.message} for tabId: $tabId" }
        val docGenerationMode = docGenerationTasks.getTask(tabId).mode
        val isEnableChatInput = docGenerationMode == Mode.EDIT &&
            (err as? DocClientException)?.remainingIterations?.let { it > 0 } ?: false

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
                            pillText = message("amazonqDoc.prompt.folder.change"),
                            type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER,
                            status = FollowUpStatusType.Info,
                        )
                    ),
                )
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

            is DocClientException -> {
                messenger.sendErrorToUser(
                    tabId = tabId,
                    errMessage = err.message,
                    conversationId = session?.conversationIdUnsafe,
                    isEnableChatInput
                )
            }

            is CodeIterationLimitException -> {
                messenger.sendUpdatePlaceholder(tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.after_monthly_limit"))
                messenger.sendChatInputEnabledMessage(tabId, enabled = true)
                messenger.sendErrorToUser(
                    tabId = tabId,
                    errMessage = err.message,
                    conversationId = session?.conversationIdUnsafe
                )

                val filePaths: List<NewFileZipInfo> = when (val state = session?.sessionState) {
                    is PrepareDocGenerationState -> state.filePaths ?: emptyList()
                    else -> emptyList()
                }

                val deletedFiles: List<DeletedFileInfo> = when (val state = session?.sessionState) {
                    is PrepareDocGenerationState -> state.deletedFiles ?: emptyList()
                    else -> emptyList()
                }

                val followUp = if (filePaths.size == 0 && deletedFiles.size == 0) {
                    listOf(
                        FollowUp(
                            pillText = message("amazonqDoc.prompt.reject.new_task"),
                            type = FollowUpTypes.NEW_TASK,
                            status = FollowUpStatusType.Info
                        ),
                        FollowUp(
                            pillText = message("amazonqDoc.prompt.reject.close_session"),
                            type = FollowUpTypes.CLOSE_SESSION,
                            status = FollowUpStatusType.Info
                        )
                    )
                } else {
                    listOf(
                        FollowUp(
                            pillText = message("amazonqDoc.prompt.review.accept"),
                            prompt = message("amazonqDoc.prompt.review.accept"),
                            status = FollowUpStatusType.Success,
                            type = FollowUpTypes.ACCEPT_CHANGES,
                            icon = FollowUpIcons.Ok,
                        ),
                        FollowUp(
                            pillText = message("general.reject"),
                            prompt = message("general.reject"),
                            status = FollowUpStatusType.Error,
                            type = FollowUpTypes.REJECT_CHANGES,
                            icon = FollowUpIcons.Cancel,
                        ),
                    )
                }

                messenger.sendSystemPrompt(
                    tabId = tabId,
                    followUp = followUp,
                )
            }

            else -> {
                var msg = createUserFacingErrorMessage("$FEATURE_NAME request failed: ${err.message ?: err.cause?.message}")
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

    private suspend fun handleChat(
        tabId: String,
        message: String,
    ) {
        var session: DocSession? = null
        val docGenerationTask = docGenerationTasks.getTask(tabId)
        try {
            logger.debug { "$FEATURE_NAME: Processing message: $message" }
            session = getSessionInfo(tabId)

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
            session.preloader(message, messenger)

            when (session.sessionState.phase) {
                SessionStatePhase.CODEGEN -> {
                    onCodeGeneration(session, message, tabId, docGenerationTask.mode)
                }

                else -> null
            }

            val filePaths: List<NewFileZipInfo> = when (val state = session.sessionState) {
                is PrepareDocGenerationState -> state.filePaths
                else -> emptyList()
            }
            sendDocGenerationTelemetry(filePaths, session, docGenerationTask)

            if (filePaths.isNotEmpty()) {
                processOpenDiff(
                    message = IncomingDocMessage.OpenDiff(tabId = tabId, filePath = filePaths[0].zipFilePath, deleted = false)
                )
            }
        } catch (err: Exception) {
            processErrorChatMessage(err, session, tabId)
        }
    }

    private suspend fun onDocsGeneration(followUpMessage: IncomingDocMessage.FollowupClicked) {
        val session = getSessionInfo(followUpMessage.tabId)
        val docGenerationTask = docGenerationTasks.getTask(followUpMessage.tabId)

        messenger.sendAnswer(
            message = docGenerationProgressMessage(DocGenerationStep.UPLOAD_TO_S3, docGenerationTask.mode),
            messageType = DocMessageType.AnswerPart,
            tabId = followUpMessage.tabId,
        )

        try {
            val sessionMessage: String = when (docGenerationTask.mode) {
                Mode.CREATE -> message("amazonqDoc.session.create")
                else -> message("amazonqDoc.session.sync")
            }

            session.send(sessionMessage)
            session.sendDocMetricData(
                MetricDataOperationName.StartDocGeneration,
                MetricDataResult.Success
            )

            val filePaths: List<NewFileZipInfo> = when (val state = session.sessionState) {
                is PrepareDocGenerationState -> state.filePaths ?: emptyList()
                else -> emptyList()
            }

            val deletedFiles: List<DeletedFileInfo> = when (val state = session.sessionState) {
                is PrepareDocGenerationState -> state.deletedFiles ?: emptyList()
                else -> emptyList()
            }

            val references: List<CodeReferenceGenerated> = when (val state = session.sessionState) {
                is PrepareDocGenerationState -> state.references ?: emptyList()
                else -> emptyList()
            }

            if (session.sessionState.token
                    ?.token
                    ?.isCancellationRequested() == true
            ) {
                return
            }

            if (filePaths.isEmpty() && deletedFiles.isEmpty()) {
                handleEmptyFiles(followUpMessage, session)
                return
            }

            sendDocGenerationTelemetry(filePaths, session, docGenerationTask)

            messenger.sendAnswer(
                message = docGenerationProgressMessage(DocGenerationStep.COMPLETE, docGenerationTask.mode),
                messageType = DocMessageType.AnswerPart,
                tabId = followUpMessage.tabId,
            )

            messenger.sendCodeResult(
                tabId = followUpMessage.tabId,
                filePaths = filePaths,
                deletedFiles = deletedFiles,
                uploadId = session.conversationId,
                references = references
            )

            messenger.sendAnswer(
                messageType = DocMessageType.Answer,
                tabId = followUpMessage.tabId,
                message = message("amazonqDoc.prompt.review.message")
            )

            messenger.sendAnswer(
                messageType = DocMessageType.SystemPrompt,
                tabId = followUpMessage.tabId,
                followUp = getFollowUpOptions(session.sessionState.phase)
            )

            processOpenDiff(
                message = IncomingDocMessage.OpenDiff(tabId = followUpMessage.tabId, filePath = filePaths[0].zipFilePath, deleted = false)
            )
        } catch (err: Exception) {
            session.sendDocMetricData(
                MetricDataOperationName.EndDocGeneration,
                session.getMetricResult(err)
            )
            processErrorChatMessage(err, session, tabId = followUpMessage.tabId)
        } finally {
            messenger.sendUpdatePlaceholder(
                tabId = followUpMessage.tabId,
                newPlaceholder = message("amazonqDoc.prompt.placeholder")
            )

            messenger.sendChatInputEnabledMessage(followUpMessage.tabId, false)

            if (session.sessionState.token
                    ?.token
                    ?.isCancellationRequested() == true
            ) {
                session.sessionState.token = CancellationTokenSource()
            } else {
                messenger.sendAsyncEventProgress(tabId = followUpMessage.tabId, inProgress = false) // Finish processing the event
                messenger.sendChatInputEnabledMessage(tabId = followUpMessage.tabId, enabled = false) // Lock chat input until a follow-up is clicked.
            }
        }
        session.sendDocMetricData(
            MetricDataOperationName.EndDocGeneration,
            MetricDataResult.Success
        )
    }

    private suspend fun handleEmptyFiles(
        message: IncomingDocMessage.FollowupClicked,
        session: DocSession,
    ) {
        messenger.sendAnswer(
            message = message("amazonqDoc.error.generating"),
            messageType = DocMessageType.Answer,
            tabId = message.tabId,
            canBeVoted = true
        )

        messenger.sendAnswer(
            messageType = DocMessageType.SystemPrompt,
            tabId = message.tabId,
            followUp = if (retriesRemaining(session) > 0) {
                listOf(
                    FollowUp(
                        pillText = message("amazonqFeatureDev.follow_up.retry"),
                        type = FollowUpTypes.RETRY,
                        status = FollowUpStatusType.Warning
                    )
                )
            } else {
                emptyList()
            }
        )

        // Lock the chat input until they explicitly click retry
        messenger.sendChatInputEnabledMessage(tabId = message.tabId, enabled = false)
    }

    private suspend fun retryRequests(tabId: String) {
        var session: DocSession? = null
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
        val docGenerationTask = docGenerationTasks.getTask(tabId)

        withContext(EDT) {
            messenger.sendAnswer(
                tabId = tabId,
                messageType = DocMessageType.Answer,
                message = message("amazonqDoc.prompt.choose_folder_to_continue")
            )

            val selectedFolder = selectFolder(context.project, workspaceRoot)

            // No folder was selected
            if (selectedFolder == null) {
                logger.info { "Cancelled dialog and not selected any folder" }
                promptForRetryFolderSelection(
                    tabId,
                    message("amazonqDoc.prompt.canceled_source_folder_selection")
                )

                return@withContext
            }

            if (!selectedFolder.path.startsWith(workspaceRoot.path)) {
                logger.info { "Selected folder not in workspace: ${selectedFolder.path}" }

                messenger.sendAnswer(
                    tabId = tabId,
                    messageType = DocMessageType.Answer,
                    message = message("amazonqFeatureDev.follow_up.incorrect_source_folder"),
                    followUp = listOf(
                        FollowUp(
                            pillText = message("amazonqDoc.prompt.folder.change"),
                            type = FollowUpTypes.MODIFY_DEFAULT_SOURCE_FOLDER,
                            status = FollowUpStatusType.Info,
                        )
                    ),
                    snapToTop = true
                )

                messenger.sendChatInputEnabledMessage(tabId, enabled = false)

                return@withContext
            }

            if (selectedFolder.path == workspaceRoot.path) {
                docGenerationTask.folderLevel = DocFolderLevel.ENTIRE_WORKSPACE
            } else {
                docGenerationTask.folderLevel = DocFolderLevel.SUB_FOLDER
            }

            logger.info { "Selected correct folder inside workspace: ${selectedFolder.path}" }

            session.context.selectionRoot = selectedFolder

            promptForDocTarget(tabId)

            messenger.sendChatInputEnabledMessage(tabId, enabled = false)

            messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqDoc.prompt.placeholder"))
        }
    }

    private fun sendDocGenerationTelemetry(filePaths: List<NewFileZipInfo>, session: DocSession, docGenerationTask: DocGenerationTask) {
        docGenerationTask.conversationId = session.conversationId
        val (totalGeneratedChars, totalGeneratedLines, totalGeneratedFiles) = session.countedGeneratedContent(filePaths, docGenerationTask.interactionType)
        docGenerationTask.numberOfGeneratedChars = totalGeneratedChars
        docGenerationTask.numberOfGeneratedLines = totalGeneratedLines
        docGenerationTask.numberOfGeneratedFiles = totalGeneratedFiles

        val docGenerationEvent = docGenerationTask.docGenerationEventBase()
        session.sendDocTelemetryEvent(docGenerationEvent)
    }

    private fun sendDocAcceptanceTelemetry(tabId: String) {
        val session = getSessionInfo(tabId)
        val docGenerationTask = docGenerationTasks.getTask(tabId)
        var filePaths: List<NewFileZipInfo> = emptyList()

        when (val state = session.sessionState) {
            is PrepareDocGenerationState -> {
                filePaths = state.filePaths
            }
        }
        docGenerationTask.conversationId = session.conversationId
        val (totalAddedChars, totalAddedLines, totalAddedFiles) = session.countAddedContent(filePaths, docGenerationTask.interactionType)
        docGenerationTask.numberOfAddedChars = totalAddedChars
        docGenerationTask.numberOfAddedLines = totalAddedLines
        docGenerationTask.numberOfAddedFiles = totalAddedFiles

        val docAcceptanceEvent = docGenerationTask.docAcceptanceEventBase()
        session.sendDocTelemetryEvent(null, docAcceptanceEvent)
    }

    private fun previewReadmeFile(tabId: String) {
        val session = getSessionInfo(tabId)
        var filePaths: List<NewFileZipInfo> = emptyList()

        when (val state = session.sessionState) {
            is PrepareDocGenerationState -> {
                filePaths = state.filePaths
            }
        }

        if (filePaths.isNotEmpty()) {
            val filePath = filePaths[0].zipFilePath
            val existingDiff = diffVirtualFiles[filePath]

            val newFilePath = session.context.addressableRoot.toNioPath().resolve(filePath)
            val readmeVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(newFilePath.toString())

            runInEdt {
                if (existingDiff != null) {
                    FileEditorManager.getInstance(getProject()).closeFile(existingDiff)
                }
                if (readmeVirtualFile != null) {
                    TextEditorWithPreview.openPreviewForFile(getProject(), readmeVirtualFile)
                }
            }
        }
    }

    fun getProject() = context.project

    private fun getSessionInfo(tabId: String) = chatSessionStorage.getSession(tabId, context.project)

    fun retriesRemaining(session: DocSession?): Int = session?.retries ?: DEFAULT_RETRY_LIMIT

    companion object {
        private val logger = getLogger<DocController>()
    }
}
