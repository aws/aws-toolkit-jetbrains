// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.inline.completion.InlineCompletionInsertHandler
import com.intellij.openapi.editor.Editor

interface QInlineCompletionInsertHandler : InlineCompletionInsertHandler {
    fun afterTyped(editor: Editor, startOffset: Int)
}
