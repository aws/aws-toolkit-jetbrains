// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer

@Deprecated(
    "Shim is no longer needed in 242+",
    ReplaceWith("InlineCompletionLineRenderer(editor, text)", "com.intellij.codeInsight.inline.completion.render.InlineCompletionLineRenderer")
)
object InlineCompletionLineRendererFactory {
    fun create(editor: Editor, text: String): EditorCustomElementRenderer =
        InlineCompletionLineRenderer(editor, text)
}
