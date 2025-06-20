// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.editor

import org.eclipse.lsp4j.TextDocumentIdentifier
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CursorState

data class ActiveEditorChangedParams(
    val textDocument: TextDocumentIdentifier?,
    val cursorState: CursorState?,
)
