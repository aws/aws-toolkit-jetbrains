// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class FollowUpClickNotification(
    val command: String,
    val params: FollowUpClickParams,
)

data class FollowUpClickParams(
    val tabId: String,
    val messageId: String,
    val followUp: ChatItemAction,
)
