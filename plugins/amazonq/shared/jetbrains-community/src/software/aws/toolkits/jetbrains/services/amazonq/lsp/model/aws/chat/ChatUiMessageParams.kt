// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat
import java.util.UUID

data class ChatUiMessageParams(
    val title: String,
    val additionalMessages: List<String> = emptyList(),
    val messageId: String = UUID.randomUUID().toString(),
    val buttons: List<String> = emptyList(),
    val codeReference: List<String> = emptyList(),
    val body: String = "",
)
