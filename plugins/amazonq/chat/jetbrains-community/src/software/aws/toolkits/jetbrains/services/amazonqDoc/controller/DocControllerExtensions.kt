// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.controller

import com.intellij.notification.NotificationAction
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.DocMessageType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAnswer
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendAsyncEventProgress
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendChatInputEnabledMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendCodeResult
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendSystemPrompt
import software.aws.toolkits.jetbrains.services.amazonqDoc.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.DocSession
import software.aws.toolkits.jetbrains.services.amazonqDoc.session.PrepareDocGenerationState
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendSystemPrompt
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.InsertAction
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.getFollowUpOptions
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message

suspend fun DocController.onCodeGeneration(session: DocSession, message: String, tabId: String, mode: Mode) {
    try {
        val sessionMessage = if (mode == Mode.CREATE) {
            message(
                "amazonqDoc.session.create"
            )
        } else if (mode == Mode.EDIT) message else message("amazonqDoc.session.sync")

        session.send(sessionMessage) // Trigger code generation

        val state = session.sessionState

        var filePaths: List<NewFileZipInfo> = emptyList()
        var deletedFiles: List<DeletedFileInfo> = emptyList()
        var references: List<CodeReferenceGenerated> = emptyList()
        var uploadId = ""
        var remainingIterations: Int? = null
        var totalIterations: Int? = null

        when (state) {
            is PrepareDocGenerationState -> {
                filePaths = state.filePaths
                deletedFiles = state.deletedFiles
                references = state.references
                uploadId = state.uploadId
                remainingIterations = state.codeGenerationRemainingIterationCount
                totalIterations = state.codeGenerationTotalIterationCount
            }
        }

        // Atm this is the only possible path as codegen is mocked to return empty.
        if (filePaths.size or deletedFiles.size == 0) {
            messenger.sendAnswer(
                tabId = tabId,
                messageType = DocMessageType.Answer,
                message = message("amazonqFeatureDev.code_generation.no_file_changes")
            )
            messenger.sendSystemPrompt(
                tabId = tabId,
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
            messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false) // Lock chat input until retry is clicked.
            return
        }

        messenger.sendCodeResult(tabId = tabId, uploadId = uploadId, filePaths = filePaths, deletedFiles = deletedFiles, references = references)

        if (remainingIterations != null && totalIterations != null) {
            messenger.sendAnswer(
                tabId = tabId,
                messageType = DocMessageType.Answer,
                message = if (remainingIterations == 0) {
                    message("amazonqFeatureDev.code_generation.iteration_zero")
                } else {
                    message(
                        "amazonqFeatureDev.code_generation.iteration_counts",
                        remainingIterations,
                        totalIterations
                    )
                }
            )
        }

        messenger.sendSystemPrompt(tabId = tabId, followUp = getFollowUpOptions(session.sessionState.phase, InsertAction.ALL))

        messenger.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.after_code_generation"))
    } finally {
        messenger.sendAsyncEventProgress(tabId = tabId, inProgress = false) // Finish processing the event
        messenger.sendChatInputEnabledMessage(tabId = tabId, enabled = false) // Lock chat input until a follow-up is clicked.

        if (toolWindow != null && !toolWindow.isVisible) {
            notifyInfo(
                title = message("amazonqFeatureDev.code_generation.notification_title"),
                content = message("amazonqFeatureDev.code_generation.notification_message"),
                project = getProject(),
                notificationActions = listOf(openChatNotificationAction())
            )
        }
    }
}

private fun DocController.openChatNotificationAction() = NotificationAction.createSimple(
    message("amazonqFeatureDev.code_generation.notification_open_link")
) {
    toolWindow?.show()
}
