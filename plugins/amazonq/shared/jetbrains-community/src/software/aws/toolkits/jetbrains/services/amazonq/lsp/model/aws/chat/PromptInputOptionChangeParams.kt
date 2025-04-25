// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

data class PromptInputOptionChangeParams(
    val tabId: String,
    val optionsValues: Map<String, String>,
    val eventId: String? = null,
)

data class PromptInputOptionChangeNotification(
    override val command: String,
    override val params: PromptInputOptionChangeParams,
) : ChatNotification<PromptInputOptionChangeParams>
