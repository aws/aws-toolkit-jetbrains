// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument

import org.eclipse.lsp4j.TextDocumentPositionAndWorkDoneProgressParams

data class InlineCompletionWithReferencesParams(
    var context: InlineCompletionContext,
) : TextDocumentPositionAndWorkDoneProgressParams()
