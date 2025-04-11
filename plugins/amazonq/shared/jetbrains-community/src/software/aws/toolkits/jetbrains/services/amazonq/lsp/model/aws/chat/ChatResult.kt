// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class ChatResult(
    val body: String,
    val messageId: String,
    val canBeVoted: Boolean,
    val relatedContent: RelatedContent,
    val followUp: FollowUp,
    val codeReference: List<ReferenceTrackerInformation>,
)

data class RelatedContent(
    val title: String,
    val content: List<SourceLink>,
)

data class FollowUp(
    val text: String,
    val options: List<ChatItemAction>,
)

data class ReferenceTrackerInformation(
    val licenseName: String,
    val repository: String,
    val url: String,
    val recommendationContentSpan: RecommendationContentSpan,
    val information: String,
)

data class SourceLink(
    val title: String,
    val url: String,
    val body: String,
)

data class ChatItemAction(
    val pillText: String,
    val prompt: String,
    val disabled: Boolean,
    val description: String,
    val type: String,
)

data class RecommendationContentSpan(
    val start: Int,
    val end: Int,
)

data class EncryptedChatResult(
    val message: String,
)
