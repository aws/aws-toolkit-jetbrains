// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.controller

import com.intellij.notification.NotificationAction
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CLIENT_ERROR_MESSAGES
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CODE_GENERATION_RETRY_LIMIT
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ClientException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.LlmException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MetricDataOperationName
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.MetricDataResult
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ServiceException
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
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.InsertAction
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
        var state = session.sessionState

        var remainingIterations: Int? = state.codeGenerationRemainingIterationCount
        var totalIterations: Int? = state.codeGenerationTotalIterationCount

        if (state.token?.token?.isCancellationRequested() == true) {
            disposeToken(messenger, tabId, state.codeGenerationRemainingIterationCount, state.codeGenerationTotalIterationCount)
            return
        }

        messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.generating_code"))

        session.sendMetricDataTelemetry(
            MetricDataOperationName.StartCodeGeneration,
            MetricDataResult.Success
        )

        session.send(message) // Trigger code generation

        state = session.sessionState

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
            disposeToken(messenger, tabId, state.codeGenerationRemainingIterationCount, state.codeGenerationTotalIterationCount)
            return
        }

        // Atm this is the only possible path as codegen is mocked to return empty.
        if (filePaths.isEmpty() && deletedFiles.isEmpty()) {
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
                if (remainingIterations > 2) {
                    message("amazonqFeatureDev.code_generation.iteration_counts_ask_to_add_code_or_feedback")
                } else if (remainingIterations > 0) {
                    message(
                        "amazonqFeatureDev.code_generation.iteration_counts",
                        remainingIterations,
                        totalIterations,
                    )
                } else {
                    message(
                        "amazonqFeatureDev.code_generation.iteration_counts_ask_to_add_code",
                        remainingIterations,
                        totalIterations,
                    )
                },
            )
        }

        messenger.sendSystemPrompt(tabId = tabId, followUp = getFollowUpOptions(session.sessionState.phase, InsertAction.ALL))
        messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.after_code_generation"))
    } catch (err: Exception) {
        val metricDataResult: MetricDataResult
        when (err) {
            is ClientException,
            -> {
                metricDataResult = MetricDataResult.Error
            }

            is LlmException -> {
                metricDataResult = MetricDataResult.LlmFailure
            }

            is ServiceException -> {
                metricDataResult = MetricDataResult.Fault
            }

            else -> {
                val errorMessage = err.message.orEmpty()
                metricDataResult = if (CLIENT_ERROR_MESSAGES.any { errorMessage.contains(it) }) {
                    MetricDataResult.Error
                } else {
                    MetricDataResult.Fault
                }
            }
        }
        session.sendMetricDataTelemetry(
            MetricDataOperationName.EndCodeGeneration,
            metricDataResult
        )
        throw err
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

    session.sendMetricDataTelemetry(
        MetricDataOperationName.EndCodeGeneration,
        MetricDataResult.Success
    )
}

private suspend fun disposeToken(
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

    if (remainingIterations !== null && totalIterations !== null && remainingIterations <= 2) {
        messenger.sendAnswer(
            tabId = tabId,
            messageType = FeatureDevMessageType.Answer,
            message =
            message(
                "amazonqFeatureDev.code_generation.stopped_code_generation",
                remainingIterations,
                totalIterations,
            ),
        )
    } else {
        messenger.sendAnswer(
            tabId = tabId,
            messageType = FeatureDevMessageType.Answer,
            message =
            message("amazonqFeatureDev.code_generation.stopped_code_generation_no_iteration_count_display"),
        )
    }

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
