// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.ui.DebuggerColors
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.inlay.CodeWhispererInlayManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationChunk
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererRecommendationManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew

class CodeWhispererUIChangeListenerNew : CodeWhispererPopupStateChangeListener {
    override fun stateChanged(sessionContext: SessionContextNew) {
        val editor = sessionContext.editor
        val editorManager = CodeWhispererEditorManagerNew.getInstance()
        val previews = CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo()
        val selectedIndex = sessionContext.selectedIndex
        val typeahead = previews[selectedIndex].typeahead
        val detail = previews[selectedIndex].detail
        val caretOffset = editor.caretModel.primaryCaret.offset
        val document = editor.document
        val lineEndOffset = document.getLineEndOffset(document.getLineNumber(caretOffset))

        detail.hasSeen = true

        // get matching brackets from recommendations to the brackets after caret position
        val remaining = CodeWhispererPopupManagerNew.getInstance().getReformattedRecommendation(
            detail,
            previews[selectedIndex].userInput,
        ).substring(typeahead.length)

        val remainingLines = remaining.split("\n")
        val firstLineOfRemaining = remainingLines.first()
        val otherLinesOfRemaining = remainingLines.drop(1)

        // process first line inlays, where we do subsequence matching as much as possible
        val matchingSymbols = editorManager.getMatchingSymbolsFromRecommendation(
            editor,
            firstLineOfRemaining,
            detail.isTruncatedOnRight,
            sessionContext
        )

        sessionContext.toBeRemovedHighlighter?.let {
            editor.markupModel.removeHighlighter(it)
        }

        // Add the strike-though hint for the remaining non-matching first-line right context for multi-line completions
        if (!detail.isTruncatedOnRight && otherLinesOfRemaining.isNotEmpty()) {
            val rangeHighlighter = editor.markupModel.addRangeHighlighter(
                matchingSymbols[matchingSymbols.size - 2].second,
                lineEndOffset,
                HighlighterLayer.LAST + 1,
                TextAttributes().apply {
                    effectType = EffectType.STRIKEOUT
                    effectColor = editor.colorsScheme.getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE).foregroundColor
                },
                HighlighterTargetArea.EXACT_RANGE
            )
            Disposer.register(sessionContext) {
                editor.markupModel.removeHighlighter(rangeHighlighter)
            }
            sessionContext.toBeRemovedHighlighter = rangeHighlighter
        }

        val chunks = CodeWhispererRecommendationManager.getInstance().buildRecommendationChunks(
            firstLineOfRemaining,
            matchingSymbols
        )

        // process other lines inlays, where we do tail-head matching as much as possible
        val overlappingLinesCount = editorManager.findOverLappingLines(
            editor,
            otherLinesOfRemaining,
            detail.isTruncatedOnRight,
            sessionContext
        )

        var otherLinesInlayText = ""
        otherLinesOfRemaining.subList(0, otherLinesOfRemaining.size - overlappingLinesCount).forEach {
            otherLinesInlayText += "\n" + it
        }

        // inlay chunks are chunks from first line(chunks) and an additional chunk from other lines
        val inlayChunks = chunks + listOf(RecommendationChunk(otherLinesInlayText, 0, chunks.last().inlayOffset))
        CodeWhispererInlayManagerNew.getInstance().updateInlays(sessionContext, inlayChunks)
        CodeWhispererPopupManagerNew.getInstance().render(
            sessionContext,
            isRecommendationAdded = false,
            isScrolling = false
        )
    }

    override fun scrolled(sessionContext: SessionContextNew) {
        sessionContext.isFirstTimeShowingPopup = false
        CodeWhispererPopupManagerNew.getInstance().render(sessionContext, isRecommendationAdded = false, isScrolling = true)
    }

    override fun recommendationAdded(states: InvocationContextNew, sessionContext: SessionContextNew) {
        sessionContext.isFirstTimeShowingPopup = false
        CodeWhispererPopupManagerNew.getInstance().render(sessionContext, isRecommendationAdded = true, isScrolling = false)
    }
}
