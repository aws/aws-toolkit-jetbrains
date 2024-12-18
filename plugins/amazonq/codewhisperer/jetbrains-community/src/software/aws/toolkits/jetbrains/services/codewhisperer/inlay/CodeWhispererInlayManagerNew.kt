// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.inlay

import com.intellij.idea.AppMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationChunk
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew

@Service
class CodeWhispererInlayManagerNew {
    private val existingInlays = mutableListOf<Inlay<EditorCustomElementRenderer>>()
    fun updateInlays(sessionContext: SessionContextNew, chunks: List<RecommendationChunk>) {
        clearInlays()

        chunks.forEach { chunk ->
            createCodeWhispererInlays(sessionContext, chunk.inlayOffset, chunk.text)
        }
    }

    private fun createCodeWhispererInlays(sessionContext: SessionContextNew, startOffset: Int, inlayText: String) {
        if (inlayText.isEmpty()) return
        val editor = sessionContext.editor
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

        if (firstLine.isNotEmpty()) {
            val firstLineRenderer =
                if (!AppMode.isRemoteDevHost()) {
                    CodeWhispererInlayInlineRenderer(firstLine)
                } else {
                    InlineCompletionRemoteRendererFactory.createLineInlay(editor, firstLine)
                }
            val inlineInlay = editor.inlayModel.addInlineElement(startOffset, true, firstLineRenderer)
            inlineInlay?.let {
                existingInlays.add(it)
                Disposer.register(sessionContext, it)
            }
        }

        if (otherLines.isEmpty()) {
            return
        }
        val otherLinesRenderers =
            if (!AppMode.isRemoteDevHost()) {
                listOf(CodeWhispererInlayBlockRenderer(otherLines))
            } else {
                InlineCompletionRemoteRendererFactory.createBlockInlays(editor, otherLines.split("\n"))
            }

        otherLinesRenderers.forEach { otherLinesRenderer ->
            val blockInlay = editor.inlayModel.addBlockElement(
                startOffset,
                true,
                false,
                0,
                otherLinesRenderer
            )
            blockInlay?.let {
                existingInlays.add(it)
                Disposer.register(sessionContext, it)
            }
        }
    }

    fun clearInlays() {
        existingInlays.forEach {
            Disposer.dispose(it)
        }
        existingInlays.clear()
    }

    companion object {
        @JvmStatic
        fun getInstance(): CodeWhispererInlayManagerNew = service()
    }
}
