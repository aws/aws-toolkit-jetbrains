// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import org.eclipse.lsp4j.TextDocumentIdentifier

data class ChatParams(
    val tabId: String,
    val prompt: ChatPrompt,
    val textDocument: TextDocumentIdentifier,
    val cursorState: CursorState,
)

data class EncryptedChatParams(
    val message: String,
    val partialResultToken: String? = null,
)
