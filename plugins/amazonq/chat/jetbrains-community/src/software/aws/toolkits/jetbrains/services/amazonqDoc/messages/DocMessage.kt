// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc.messages

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthFollowUpType
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.DeletedFileInfo
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.NewFileZipInfo
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import java.time.Instant
import java.util.UUID

sealed interface DocBaseMessage : AmazonQMessage

// === UI -> App Messages ===
sealed interface IncomingDocMessage : DocBaseMessage {

    data class ChatPrompt(
        val chatMessage: String,
        val command: String,
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingDocMessage

    data class NewTabCreated(
        val command: String,
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingDocMessage

    data class AuthFollowUpWasClicked(
        @JsonProperty("tabID") val tabId: String,
        val authType: AuthFollowUpType,
    ) : IncomingDocMessage

    data class TabRemoved(
        val command: String,
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingDocMessage

    data class FollowupClicked(
        val followUp: FollowUp,
        @JsonProperty("tabID") val tabId: String,
        val messageId: String?,
        val command: String,
        val tabType: String,
    ) : IncomingDocMessage

    data class ChatItemVotedMessage(
        @JsonProperty("tabID") val tabId: String,
        val messageId: String,
        val vote: String,
    ) : IncomingDocMessage

    data class ChatItemFeedbackMessage(
        @JsonProperty("tabID") val tabId: String,
        val selectedOption: String,
        val comment: String?,
        val messageId: String,
    ) : IncomingDocMessage

    data class ClickedLink(
        @JsonProperty("tabID") val tabId: String,
        val command: String,
        val messageId: String?,
        val link: String,
    ) : IncomingDocMessage

    data class InsertCodeAtCursorPosition(
        @JsonProperty("tabID") val tabId: String,
        val code: String,
        val insertionTargetType: String?,
        val codeReference: List<CodeReference>?,
    ) : IncomingDocMessage

    data class OpenDiff(
        @JsonProperty("tabID") val tabId: String,
        val filePath: String,
        val deleted: Boolean,
    ) : IncomingDocMessage

    data class FileClicked(
        @JsonProperty("tabID") val tabId: String,
        val filePath: String,
        val messageId: String,
        val actionName: String,
    ) : IncomingDocMessage

    data class StopDocGeneration(
        @JsonProperty("tabID") val tabId: String,
    ) : IncomingDocMessage
}

// === UI -> App Messages ===

sealed class UiMessage(
    open val tabId: String?,
    open val type: String,
) : DocBaseMessage {
    val time = Instant.now().epochSecond
    val sender = "docChat"
}

enum class DocMessageType(
    @field:JsonValue val json: String,
) {
    Answer("answer"),
    AnswerPart("answer-part"),
    AnswerStream("answer-stream"),
    SystemPrompt("system-prompt"),
}

data class DocMessage(
    @JsonProperty("tabID") override val tabId: String,
    @JsonProperty("triggerID") val triggerId: String,
    val messageType: DocMessageType,
    val messageId: String,
    val message: String? = null,
    val followUps: List<FollowUp>? = null,
    val canBeVoted: Boolean,
    val snapToTop: Boolean,

) : UiMessage(
    tabId = tabId,
    type = "chatMessage",
)

data class AsyncEventProgressMessage(
    @JsonProperty("tabID") override val tabId: String,
    val message: String? = null,
    val inProgress: Boolean,
) : UiMessage(
    tabId = tabId,
    type = "asyncEventProgressMessage"
)

data class UpdatePlaceholderMessage(
    @JsonProperty("tabID") override val tabId: String,
    val newPlaceholder: String,
) : UiMessage(
    tabId = tabId,
    type = "updatePlaceholderMessage"
)

data class FileComponent(
    @JsonProperty("tabID") override val tabId: String,
    val filePaths: List<NewFileZipInfo>,
    val deletedFiles: List<DeletedFileInfo>,
    val messageId: String,
) : UiMessage(
    tabId = tabId,
    type = "updateFileComponent"
)

data class ChatInputEnabledMessage(
    @JsonProperty("tabID") override val tabId: String,
    val enabled: Boolean,
) : UiMessage(
    tabId = tabId,
    type = "chatInputEnabledMessage"
)
data class ErrorMessage(
    @JsonProperty("tabID") override val tabId: String,
    val title: String,
    val message: String,
) : UiMessage(
    tabId = tabId,
    type = "errorMessage",
)

data class FolderConfirmationMessage(
    @JsonProperty("tabID") override val tabId: String,
    val folderPath: String,
    val message: String,
    val followUps: List<FollowUp>?,
) : UiMessage(
    tabId = tabId,
    type = "folderConfirmationMessage"
)

// this should come from mynah?
data class ChatItemButton(
    val id: String,
    val text: String,
    val icon: String,
    val keepCardAfterClick: Boolean,
    val disabled: Boolean,
    val waitMandatoryFormItems: Boolean,
)

data class ProgressField(
    val status: String,
    val text: String,
    val value: Int,
    var actions: List<ChatItemButton>,
)

data class AuthenticationUpdateMessage(
    val authenticatingTabIDs: List<String>,
    val featureDevEnabled: Boolean,
    val codeTransformEnabled: Boolean,
    val codeScanEnabled: Boolean,
    val codeTestEnabled: Boolean,
    val docEnabled: Boolean,
    val message: String? = null,
    val messageId: String = UUID.randomUUID().toString(),
) : UiMessage(
    null,
    type = "authenticationUpdateMessage",
)

data class AuthNeededException(
    @JsonProperty("tabID") override val tabId: String,
    @JsonProperty("triggerID") val triggerId: String,
    val authType: AuthFollowUpType,
    val message: String,
) : UiMessage(
    tabId = tabId,
    type = "authNeededException",
)

data class CodeResultMessage(
    @JsonProperty("tabID") override val tabId: String,
    val conversationId: String,
    val filePaths: List<NewFileZipInfo>,
    val deletedFiles: List<DeletedFileInfo>,
    val references: List<CodeReference>,
) : UiMessage(
    tabId = tabId,
    type = "codeResultMessage"
)

data class FollowUp(
    val type: FollowUpTypes,
    val pillText: String,
    val prompt: String? = null,
    val disabled: Boolean? = false,
    val description: String? = null,
    val status: FollowUpStatusType? = null,
    val icon: FollowUpIcons? = null,
)

enum class FollowUpIcons(
    @field:JsonValue val json: String,
) {
    Ok("ok"),
    Refresh("refresh"),
    Cancel("cancel"),
    Info("info"),
    Error("error"),
}

enum class FollowUpStatusType(
    @field:JsonValue val json: String,
) {
    Info("info"),
    Success("success"),
    Warning("warning"),
    Error("error"),
}

enum class FollowUpTypes(
    @field:JsonValue val json: String,
) {
    RETRY("Retry"),
    MODIFY_DEFAULT_SOURCE_FOLDER("ModifyDefaultSourceFolder"),
    DEV_EXAMPLES("DevExamples"),
    INSERT_CODE("InsertCode"),
    PROVIDE_FEEDBACK_AND_REGENERATE_CODE("ProvideFeedbackAndRegenerateCode"),
    NEW_TASK("NewTask"),
    CLOSE_SESSION("CloseSession"),
    CREATE_DOCUMENTATION("CreateDocumentation"),
    UPDATE_DOCUMENTATION("UpdateDocumentation"),
    CANCEL_FOLDER_SELECTION("CancelFolderSelection"),
    PROCEED_FOLDER_SELECTION("ProceedFolderSelection"),
    ACCEPT_CHANGES("AcceptChanges"),
    MAKE_CHANGES("MakeChanges"),
    REJECT_CHANGES("RejectChanges"),
    SYNCHRONIZE_DOCUMENTATION("SynchronizeDocumentation"),
    EDIT_DOCUMENTATION("EditDocumentation"),
}

// Util classes
data class ReducedCodeReference(
    val information: String,
)
