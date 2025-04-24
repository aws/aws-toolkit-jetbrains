// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class ButtonClickNotification(
    override val command: String,
    override val params: ButtonClickParams,
) : ChatNotification<ButtonClickParams>

data class ButtonClickParams(
    val tabId: String,
    val messageId: String,
    val buttonId: String,
)

data class ButtonClickResult(
    val success: Boolean,
    val failureReason: String?,
)
