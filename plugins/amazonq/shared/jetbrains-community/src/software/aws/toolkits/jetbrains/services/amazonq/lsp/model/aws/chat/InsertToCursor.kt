// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier

data class InsertToCursorPositionNotification(
    override val command: String,
    override val params: InsertToCursorPositionParams,
) : ChatNotification<InsertToCursorPositionParams>

data class InsertToCursorPositionParams(
    val tabId: String,
    val messageId: String,
    val cursorPosition: Position? = null,
    val textDocument: TextDocumentIdentifier? = null,
    val code: String? = null,
    val type: String? = null,
    val referenceTrackerInformation: List<ReferenceTrackerInformation>? = null,
    val eventId: String? = null,
    val codeBlockIndex: Int? = null,
    val totalCodeBlocks: Int? = null,
)
