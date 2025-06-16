// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import com.fasterxml.jackson.annotation.JsonProperty
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.ContextCommand

data class ChatPrompt(
    val prompt: String,
    val escapedPrompt: String,
    val command: String?,
)

data class SendChatPromptRequest(
    val command: String,
    val params: MidChatPrompt,
)

data class MidChatPrompt(
    val prompt: InnerChatPrompt,
    val tabId: String,
    val context: List<ContextCommand>?,
)

data class InnerChatPrompt(
    val prompt: String,
    val escapedPrompt: String,
    val context: List<ContextCommand>? = null,
    val options: InnerChatOptions?,
)

data class InnerChatOptions(
    @JsonProperty("pair-programmer-mode")
    val pairProgrammingMode: String?,
)
