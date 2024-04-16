// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.inlay

import com.intellij.codeInsight.inline.completion.render.InlineBlockElementRenderer
import com.intellij.codeInsight.inline.completion.render.InlineSuffixRenderer
import com.intellij.idea.AppMode
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationChunk

class CodeWhispererInlayManager {
    fun updateInlays(states: InvocationContext, chunks: List<RecommendationChunk>) {
        val editor = states.requestContext.editor
        clearInlays(editor)

        chunks.forEach { chunk ->
            createCodeWhispererInlays(editor, chunk.inlayOffset, chunk.text, states.popup)
        }
    }

    private fun createCodeWhispererInlays(editor: Editor, startOffset: Int, inlayText: String, popup: JBPopup) {
        if (inlayText.isEmpty()) return
        val firstNewlineIndex = inlayText.indexOf("\n")
        val firstLine: String
        val otherLines: String
        if (firstNewlineIndex != -1 && firstNewlineIndex < inlayText.length - 1) {
            firstLine = inlayText.substring(0, firstNewlineIndex)
            otherLines = inlayText.substring(firstNewlineIndex + 1)
        } else {
            firstLine = inlayText
            otherLines = ""
        }

        val firstLineRenderer =
            if (!AppMode.isRemoteDevHost()) {
                CodeWhispererInlayInlineRenderer(firstLine)
            } else {
                InlineSuffixRenderer(editor, firstLine)
            }
        val inlineInlay = editor.inlayModel.addInlineElement(startOffset, true, firstLineRenderer)
        inlineInlay?.let { Disposer.register(popup, it) }

        if (otherLines.isEmpty()) {
            return
        }
        val otherLinesRenderer =
            if (!AppMode.isRemoteDevHost()) {
                CodeWhispererInlayBlockRenderer(otherLines)
            } else {
                InlineBlockElementRenderer(editor, otherLines.split("\n"))
            }
        val blockInlay = editor.inlayModel.addBlockElement(
            startOffset,
            true,
            false,
            0,
            otherLinesRenderer
        )
        blockInlay?.let { Disposer.register(popup, it) }
    }

    fun clearInlays(editor: Editor) {
        if (!AppMode.isRemoteDevHost()) {
            editor.inlayModel.getInlineElementsInRange(
                0,
                editor.document.textLength,
                CodeWhispererInlayInlineRenderer::class.java
            ).forEach { disposable ->
                Disposer.dispose(disposable)
            }
            editor.inlayModel.getBlockElementsInRange(
                0,
                editor.document.textLength,
                CodeWhispererInlayBlockRenderer::class.java
            ).forEach { disposable ->
                Disposer.dispose(disposable)
            }
        } else {
            editor.inlayModel.getInlineElementsInRange(
                0,
                editor.document.textLength,
                InlineSuffixRenderer::class.java
            ).forEach { disposable ->
                Disposer.dispose(disposable)
            }
            editor.inlayModel.getBlockElementsInRange(
                0,
                editor.document.textLength,
                InlineBlockElementRenderer::class.java
            ).forEach { disposable ->
                Disposer.dispose(disposable)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): CodeWhispererInlayManager = service()
    }
}
