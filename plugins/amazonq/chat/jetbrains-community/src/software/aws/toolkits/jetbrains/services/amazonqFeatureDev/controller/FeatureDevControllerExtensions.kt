// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.controller

import com.intellij.notification.NotificationAction
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CODE_GENERATION_RETRY_LIMIT
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FeatureDevMessageType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswer
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendCodeResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendSystemPrompt
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.PrepareCodeGenerationState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.Session
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.getFollowUpOptions
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

suspend fun FeatureDevController.onCodeGeneration(
    session: Session,
    message: String,
    tabId: String,
) {
    messenger.sendAsyncEventProgress(
        tabId = tabId,
        inProgress = true,
        message = if (session.retries == CODE_GENERATION_RETRY_LIMIT) {
            message(
                "amazonqFeatureDev.chat_message.start_code_generation",
            )
        } else {
            message("amazonqFeatureDev.chat_message.start_code_generation_retry")
        },
    )

    try {
        this.messenger.sendAnswer(
            tabId = tabId,
            message = message("amazonqFeatureDev.chat_message.requesting_changes"),
            messageType = FeatureDevMessageType.AnswerStream,
        )
        val state = session.sessionState

        var remainingIterations: Int? = state.codeGenerationRemainingIterationCount
        var totalIterations: Int? = state.codeGenerationTotalIterationCount

        if (state.token?.token?.isCancellationRequested() == true) {
            disposeToken(state, messenger, tabId, state.currentIteration?.let { CODE_GENERATION_RETRY_LIMIT.minus(it) }, CODE_GENERATION_RETRY_LIMIT)
            return
        }

        messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.generating_code"))

        session.send(message) // Trigger code generation

        var filePaths: List<NewFileZipInfo> = emptyList()
        var deletedFiles: List<DeletedFileInfo> = emptyList()
        var references: List<CodeReferenceGenerated> = emptyList()
        var uploadId = ""

        when (state) {
            is PrepareCodeGenerationState -> {
                filePaths = state.filePaths
                deletedFiles = state.deletedFiles
                references = state.references
                uploadId = state.uploadId
                remainingIterations = state.codeGenerationRemainingIterationCount
                totalIterations = state.codeGenerationTotalIterationCount
            }
        }

        if (state.token?.token?.isCancellationRequested() == true) {
            disposeToken(state, messenger, tabId, state.currentIteration?.let { CODE_GENERATION_RETRY_LIMIT.minus(it) }, CODE_GENERATION_RETRY_LIMIT)
            return
        }

        // Atm this is the only possible path as codegen is mocked to return empty.
        if (filePaths.size or deletedFiles.size == 0) {
            messenger.sendAnswer(
                tabId = tabId,
                messageType = FeatureDevMessageType.Answer,
                message = message("amazonqFeatureDev.code_generation.no_file_changes"),
            )
            messenger.sendSystemPrompt(
                tabId = tabId,
                followUp =
                if (retriesRemaining(session) > 0) {
                    listOf(
                        FollowUp(
                            pillText = message("amazonqFeatureDev.follow_up.retry"),
                            type = FollowUpTypes.RETRY,
                            status = FollowUpStatusType.Warning,
                        ),
                    )
                } else {
                    emptyList()
                },
            )
            messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false) // Lock chat input until retry is clicked.
            return
        }

        messenger.sendCodeResult(tabId = tabId, uploadId = uploadId, filePaths = filePaths, deletedFiles = deletedFiles, references = references)

        if (remainingIterations != null && totalIterations != null) {
            messenger.sendAnswer(
                tabId = tabId,
                messageType = FeatureDevMessageType.Answer,
                message =
                if (remainingIterations == 0) {
                    message("amazonqFeatureDev.code_generation.iteration_zero")
                } else {
                    message(
                        "amazonqFeatureDev.code_generation.iteration_counts",
                        remainingIterations,
                        totalIterations,
                    )
                },
            )
        }

        messenger.sendSystemPrompt(tabId = tabId, followUp = getFollowUpOptions(session.sessionState.phase))

        messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.after_code_generation"))
    } finally {
        if (session.sessionState.token
                ?.token
                ?.isCancellationRequested() == true
        ) {
            session.sessionState.token = CancellationTokenSource()
        } else {
            messenger.sendAsyncEventProgress(tabId = tabId, inProgress = false) // Finish processing the event
            messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false) // Lock chat input until a follow-up is clicked.
        }
        if (toolWindow != null && !toolWindow.isVisible) {
            notifyInfo(
                title = message("amazonqFeatureDev.code_generation.notification_title"),
                content = message("amazonqFeatureDev.code_generation.notification_message"),
                project = getProject(),
                notificationActions = listOf(openChatNotificationAction()),
            )
        }
    }
}

private suspend fun disposeToken(
    state: SessionState,
    messenger: MessagePublisher,
    tabId: String,
    remainingIterations: Int?,
    totalIterations: Int?,
) {
    if (remainingIterations !== null && remainingIterations <= 0) {
        messenger.sendAnswer(
            tabId = tabId,
            messageType = FeatureDevMessageType.Answer,
            message =
            message(
                "amazonqFeatureDev.code_generation.stopped_code_generation_no_iterations",
            ),
        )
        // I stopped generating your code. You don't have more iterations left, however, you can start a new session
        messenger.sendSystemPrompt(
            tabId = tabId,
            followUp =
            listOf(
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.new_task"),
                    type = FollowUpTypes.NEW_TASK,
                    status = FollowUpStatusType.Info,
                ),
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.close_session"),
                    type = FollowUpTypes.CLOSE_SESSION,
                    status = FollowUpStatusType.Info,
                ),
            ),
        )
        messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false)
        messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.after_code_generation"))

        return
    }

    messenger.sendAnswer(
        tabId = tabId,
        messageType = FeatureDevMessageType.Answer,
        message =
        message(
            "amazonqFeatureDev.code_generation.stopped_code_generation",
            remainingIterations ?: state.currentIteration?.let { CODE_GENERATION_RETRY_LIMIT - it } as Any,
            totalIterations ?: CODE_GENERATION_RETRY_LIMIT,
        ),
    )

    messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = true)

    messenger.sendUpdatePlaceholder(
        tabId = tabId,
        newPlaceholder = message("amazonqFeatureDev.placeholder.new_plan"),
    )
}

private fun FeatureDevController.openChatNotificationAction() =
    NotificationAction.createSimple(
        message("amazonqFeatureDev.code_generation.notification_open_link"),
    ) {
        toolWindow?.show()
    }
