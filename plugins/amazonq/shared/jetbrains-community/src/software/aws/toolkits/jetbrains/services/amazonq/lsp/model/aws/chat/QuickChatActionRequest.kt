// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class QuickChatActionRequest(
    val command: String,
    val params: QuickChatActionPrompt,
)

data class QuickChatActionPrompt(
    val tabId: String,
    val quickAction: String,
    val prompt: String,
)

data class EncryptedQuickActionChatParams(
    val message: String,
    val partialResultToken: String? = null,
)
