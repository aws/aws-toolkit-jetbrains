// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class FeedbackNotification(
    override val command: String,
    override val params: FeedbackParams,
) : ChatNotification<FeedbackParams>

data class FeedbackParams(
    val tabId: String,
    val feedbackPayload: FeedbackPayload,
    val eventId: String?,
)

data class FeedbackPayload(
    val messageId: String,
    val tabId: String,
    val selectedOption: String,
    val comment: String? = null,
)
