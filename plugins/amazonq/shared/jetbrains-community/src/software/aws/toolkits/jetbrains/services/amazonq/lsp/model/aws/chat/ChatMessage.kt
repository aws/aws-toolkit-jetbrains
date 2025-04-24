// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

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
    FILE,
    FOLDER,
    CODE_BLOCK,
    LIST_ADD,
    MAGIC,
    HELP,
    TRASH,
    SEARCH,
    CALENDAR,
    ;

    val value: String
        get() = name.lowercase().replace('_', '-')

    companion object {
        private val stringToEnum: Map<String, IconType> = entries.associateBy { it.name.lowercase() }

        fun fromString(value: String): IconType = stringToEnum[value] ?: throw IllegalArgumentException("Unknown IconType: $value")
    }
}

enum class Status {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    ;

    val value: String
        get() = name.lowercase()
}

enum class ButtonStatus {
    MAIN,
    PRIMARY,
    CLEAR,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    ;

    val value: String
        get() = name.lowercase()
}

enum class MessageType {
    ANSWER,
    PROMPT,
    SYSTEM_PROMPT,
    DIRECTIVE,
    TOOL,
    ;

    val value: String
        get() = name.lowercase().replace('_', '-')
}
