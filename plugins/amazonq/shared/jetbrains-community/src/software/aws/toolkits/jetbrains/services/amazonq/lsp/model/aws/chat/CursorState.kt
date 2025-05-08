// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat

import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Position

sealed interface CursorState

data class CursorPosition(
    val position: Position,
) : CursorState

data class CursorRange(
    val range: Range,
) : CursorState
