// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatMessage(
    val type: MessageType? = MessageType.ANSWER,
    val header: MessageHeader? = null,
    val buttons: List<Button>? = null,
    val body: String? = null,
    val messageId: String? = null,
    val canBeVoted: Boolean? = null,
    val relatedContent: RelatedContent? = null,
    val followUp: FollowUp? = null,
    val codeReference: List<ReferenceTrackerInformation>? = null,
    val fileList: FileList? = null,
    val contextList: FileList? = null,
)

data class MessageHeader(
    val type: MessageType? = MessageType.ANSWER,
    val buttons: List<Button>? = null,
    val body: String? = null,
    val messageId: String? = null,
    val canBeVoted: Boolean? = null,
    val relatedContent: RelatedContent? = null,
    val followUp: FollowUp? = null,
    val codeReference: List<ReferenceTrackerInformation>? = null,
    val fileList: FileList? = null,
    val contextList: FileList? = null,
    val icon: IconType? = null,
    val status: MessageStatus? = null,
)

data class MessageStatus(
    val status: Status? = null,
    val icon: IconType? = null,
    val text: String? = null,
)

data class Button(
    val id: String,
    val text: String? = null,
    val description: String? = null,
    val icon: IconType? = null,
    val disabled: Boolean? = null,
    val keepCardAfterClick: Boolean? = null,
    val status: ButtonStatus? = null,
)

data class FileList(
    val rootFolderTitle: String? = null,
    val filePaths: List<String>? = null,
    val deletedFiles: List<String>? = null,
    val details: Map<String, FileDetails>? = null,
)

data class FileDetails(
    val description: String? = null,
    val lineRanges: List<Pair<Int, Int>>? = null,
    val changes: Changes? = null,
)

data class Changes(
    val added: Int? = null,
    val deleted: Int? = null,
    val total: Int? = null,
)

enum class IconType {
    @JsonProperty("file")
    FILE,

    @JsonProperty("folder")
    FOLDER,

    @JsonProperty("code-block")
    CODE_BLOCK,

    @JsonProperty("list-add")
    LIST_ADD,

    @JsonProperty("magic")
    MAGIC,

    @JsonProperty("help")
    HELP,

    @JsonProperty("trash")
    TRASH,

    @JsonProperty("search")
    SEARCH,

    @JsonProperty("calendar")
    CALENDAR,
    ;

    companion object {
        private val stringToEnum: Map<String, IconType> = entries.associateBy { it.name.lowercase() }

        fun fromString(value: String): IconType = stringToEnum[value] ?: throw IllegalArgumentException("Unknown IconType: $value")
    }
}

enum class Status {
    @JsonProperty("info")
    INFO,

    @JsonProperty("success")
    SUCCESS,

    @JsonProperty("warning")
    WARNING,

    @JsonProperty("error")
    ERROR,
}

enum class ButtonStatus {
    @JsonProperty("main")
    MAIN,

    @JsonProperty("primary")
    PRIMARY,

    @JsonProperty("clear")
    CLEAR,

    @JsonProperty("info")
    INFO,

    @JsonProperty("success")
    SUCCESS,

    @JsonProperty("warning")
    WARNING,

    @JsonProperty("error")
    ERROR,
}

enum class MessageType {
    @JsonProperty("answer")
    ANSWER,

    @JsonProperty("prompt")
    PROMPT,

    @JsonProperty("system-prompt")
    SYSTEM_PROMPT,

    @JsonProperty("directive")
    DIRECTIVE,

    @JsonProperty("tool")
    TOOL,
}
