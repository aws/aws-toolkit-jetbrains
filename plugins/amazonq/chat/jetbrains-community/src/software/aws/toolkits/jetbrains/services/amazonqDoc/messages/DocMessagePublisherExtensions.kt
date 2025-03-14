// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.messages

import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthNeededState
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.ProgressField
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.PromptProgressMessage
import software.aws.toolkits.jetbrains.services.amazonqDoc.ui.NEW_SESSION_FOLLOWUPS
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.CodeReferenceGenerated
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import software.aws.toolkits.jetbrains.services.cwc.messages.RecommendationContentSpan
import software.aws.toolkits.resources.message
import java.util.Collections
import java.util.UUID

suspend fun MessagePublisher.sendAnswer(
    tabId: String,
    message: String? = null,
    messageType: DocMessageType,
    followUp: List<FollowUp>? = null,
    canBeVoted: Boolean? = false,
    snapToTop: Boolean? = false,
) {
    val chatMessage =
        DocMessage(
            tabId = tabId,
            triggerId = UUID.randomUUID().toString(),
            messageId = UUID.randomUUID().toString(),
            messageType = messageType,
            message = message,
            followUps = followUp,
            canBeVoted = canBeVoted ?: false,
            snapToTop = snapToTop ?: false
        )
    this.publish(chatMessage)
}

suspend fun MessagePublisher.sendAnswerPart(
    tabId: String,
    message: String? = null,
    canBeVoted: Boolean? = null,
) {
    this.sendAnswer(
        tabId = tabId,
        message = message,
        messageType = DocMessageType.AnswerPart,
        canBeVoted = canBeVoted
    )
}

suspend fun MessagePublisher.sendSystemPrompt(
    tabId: String,
    followUp: List<FollowUp>,
) {
    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.SystemPrompt,
        followUp = followUp
    )
}

suspend fun MessagePublisher.updateFileComponent(tabId: String, filePaths: List<NewFileZipInfo>, deletedFiles: List<DeletedFileInfo>, messageId: String) {
    val fileComponentMessage = FileComponent(
        tabId = tabId,
        filePaths = filePaths,
        deletedFiles = deletedFiles,
        messageId = messageId,
    )
    this.publish(fileComponentMessage)
}

suspend fun MessagePublisher.sendAsyncEventProgress(tabId: String, inProgress: Boolean, message: String? = null) {
    val asyncEventProgressMessage = AsyncEventProgressMessage(
        tabId = tabId,
        message = message,
        inProgress = inProgress,
    )
    this.publish(asyncEventProgressMessage)
}

suspend fun MessagePublisher.sendUpdatePlaceholder(tabId: String, newPlaceholder: String) {
    val updatePlaceholderMessage = UpdatePlaceholderMessage(
        tabId = tabId,
        newPlaceholder = newPlaceholder
    )
    this.publish(updatePlaceholderMessage)
}

suspend fun MessagePublisher.sendAuthNeededException(tabId: String, triggerId: String, credentialState: AuthNeededState) {
    val message = AuthNeededException(
        tabId = tabId,
        triggerId = triggerId,
        authType = credentialState.authType,
        message = credentialState.message,
    )
    this.publish(message)
}

suspend fun MessagePublisher.sendAuthenticationInProgressMessage(tabId: String) {
    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.Answer,
        message = message("amazonqFeatureDev.follow_instructions_for_authentication")
    )
}
suspend fun MessagePublisher.sendChatInputEnabledMessage(tabId: String, enabled: Boolean) {
    val chatInputEnabledMessage = ChatInputEnabledMessage(
        tabId,
        enabled,
    )
    this.publish(chatInputEnabledMessage)
}

suspend fun MessagePublisher.sendError(tabId: String, errMessage: String?, retries: Int, conversationId: String? = null, showDefaultMessage: Boolean? = false) {
    val conversationIdText = if (conversationId == null) "" else "\n\nConversation ID: **$conversationId**"

    if (retries == 0) {
        this.sendAnswer(
            tabId = tabId,
            messageType = DocMessageType.Answer,
            message = if (showDefaultMessage == true) errMessage else message("amazonqFeatureDev.no_retries.error_text") + conversationIdText,
        )

        this.sendAnswer(
            tabId = tabId,
            messageType = DocMessageType.SystemPrompt,
        )
        return
    }

    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.Answer,
        message = errMessage + conversationIdText,
    )

    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.SystemPrompt,
        followUp = listOf(
            FollowUp(
                pillText = message("amazonqFeatureDev.follow_up.retry"),
                type = FollowUpTypes.RETRY,
                status = FollowUpStatusType.Warning
            )
        ),
    )
}

suspend fun MessagePublisher.sendErrorToUser(
    tabId: String,
    errMessage: String?,
    conversationId: String? = null,
    isEnableChatInput: Boolean = false,
) {
    val conversationIdText = if (conversationId == null) "" else "\n\nConversation ID: **$conversationId**"

    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.Answer,
        message = errMessage + conversationIdText,
    )

    if (isEnableChatInput) {
        this.sendAnswer(
            tabId = tabId,
            messageType = DocMessageType.SystemPrompt,
            followUp = Collections.emptyList()
        )
        this.sendUpdatePlaceholder(tabId, message("amazonqDoc.edit.placeholder"))
    } else {
        this.sendAnswer(
            tabId = tabId,
            messageType = DocMessageType.SystemPrompt,
            followUp = NEW_SESSION_FOLLOWUPS
        )
        this.sendUpdatePlaceholder(tabId, message("amazonqDoc.prompt.placeholder"))
    }

    this.sendChatInputEnabledMessage(tabId, enabled = isEnableChatInput)
}

suspend fun MessagePublisher.sendMonthlyLimitError(tabId: String) {
    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.Answer,
        message = message("amazonqFeatureDev.exception.monthly_limit_error")
    )
    this.sendUpdatePlaceholder(tabId = tabId, newPlaceholder = message("amazonqFeatureDev.placeholder.after_monthly_limit"))
}

suspend fun MessagePublisher.initialExamples(tabId: String) {
    this.sendAnswer(
        tabId = tabId,
        messageType = DocMessageType.Answer,
        message = message("amazonqFeatureDev.example_text"),
    )
}

suspend fun MessagePublisher.sendCodeResult(
    tabId: String,
    uploadId: String,
    filePaths: List<NewFileZipInfo>,
    deletedFiles: List<DeletedFileInfo>,
    references: List<CodeReferenceGenerated>,
) {
    val refs = references.map { ref ->
        CodeReference(
            licenseName = ref.licenseName,
            repository = ref.repository,
            url = ref.url,
            recommendationContentSpan = RecommendationContentSpan(
                ref.recommendationContentSpan?.start ?: 0,
                ref.recommendationContentSpan?.end ?: 0,
            ),
            information = "Reference code under **${ref.licenseName}** license from repository [${ref.repository}](${ref.url})"
        )
    }

    this.publish(
        CodeResultMessage(
            tabId = tabId,
            conversationId = uploadId,
            filePaths = filePaths,
            deletedFiles = deletedFiles,
            references = refs
        )
    )
}

suspend fun MessagePublisher.sendFolderConfirmationMessage(
    tabId: String,
    message: String,
    folderPath: String,
    followUps: List<FollowUp>,
) {
    this.publish(
        FolderConfirmationMessage(tabId = tabId, folderPath = folderPath, message = message, followUps = followUps)
    )
}

suspend fun MessagePublisher.sendRetryChangeFolderMessage(
    tabId: String,
    message: String,
    followUps: List<FollowUp>,
) {
    this.publish(
        RetryChangeFolderMessage(tabId = tabId, message = message, followUps = followUps)
    )
}

suspend fun MessagePublisher.sendUpdatePromptProgress(tabId: String, progressField: ProgressField?) {
    this.publish(
        PromptProgressMessage(tabId, progressField)
    )
}
