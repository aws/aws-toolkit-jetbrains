// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonValue
import com.google.gson.annotations.JsonAdapter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EnumJsonValueAdapter

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
    val summary: Summary? = null,
)

data class Summary(
    val content: ChatMessage? = null,
    val collapsedContent: List<ChatMessage>? = null,
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

// i don't want to model 70+ icon types
// https://github.com/aws/mynah-ui/blob/38608dff905b3790d85c73e2911ec7071c8a8cdf/src/components/icon.ts#L12
typealias IconType = String

@JsonAdapter(EnumJsonValueAdapter::class)
enum class Status(@JsonValue val repr: String) {
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error"),
}

@JsonAdapter(EnumJsonValueAdapter::class)
enum class ButtonStatus(@JsonValue val repr: String) {
    MAIN("main"),
    PRIMARY("primary"),
    CLEAR("clear"),
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error"),
}

// https://github.com/aws/language-server-runtimes/blame/68319c975d29a8ba9b084c9fa780ebff75b286bb/types/chat.ts#L127
@JsonAdapter(EnumJsonValueAdapter::class)
enum class MessageType(@JsonValue val repr: String) {
    ANSWER("answer"),
    PROMPT("prompt"),
    SYSTEM_PROMPT("system-prompt"),
    DIRECTIVE("directive"),
    TOOL("tool"),
}

val CODE_REVIEW_FINDINGS_SUFFIX = "_codeReviewFindings"
val DISPLAY_FINDINGS_SUFFIX = "_displayFindings"
