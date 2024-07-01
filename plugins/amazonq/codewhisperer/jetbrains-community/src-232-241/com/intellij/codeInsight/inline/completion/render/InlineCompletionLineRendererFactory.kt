// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer

// from 232-241.2, we have `InlineSuffixRenderer`, but with 241.3+ it becomes `InlineCompletionLineRenderer`
object InlineCompletionLineRendererFactory {
    private val clazz by lazy {
        try {
            Class.forName("com.intellij.codeInsight.inline.completion.render.InlineSuffixRenderer")
        } catch (e: ClassNotFoundException) {
            Class.forName("com.intellij.codeInsight.inline.completion.render.InlineCompletionLineRenderer")
        }
    }

    fun create(editor: Editor, text: String): EditorCustomElementRenderer =
        clazz.getConstructor(Editor::class.java, String::class.java)
            .newInstance(editor, text) as EditorCustomElementRenderer
}
