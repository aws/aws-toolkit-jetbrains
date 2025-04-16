// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class ChatPrompt(
    val prompt: String,
    val escapedPrompt: String,
    val command: String,
)

data class SendChatPromptRequest(
    val command: String,
    val params: MidChatPrompt,
)

data class MidChatPrompt(
    val prompt: InnerChatPrompt,
    val tabId: String,
)

data class InnerChatPrompt(
    val prompt: String,
    val escapedPrompt: String,
    val context: List<String>? = null,
)
