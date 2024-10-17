// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline.listeners

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatGutterIconRenderer
import software.aws.toolkits.jetbrains.services.cwc.inline.OpenChatInputAction

class ChatCaretListener : CaretListener {
    private var currentHighlighter: RangeHighlighter? = null

    private fun createGutterIconRenderer(editor: Editor): GutterIconRenderer = InlineChatGutterIconRenderer(AwsIcons.Logos.AWS_Q_GREY).apply {
        setClickAction {
            val action = OpenChatInputAction()
            val dataContext = DataManager.getInstance().getDataContext(editor.component)
            val e = AnActionEvent.createFromDataContext(
                "GutterIconClick",
                Presentation(),
                dataContext
            )
            action.actionPerformed(e)
        }
    }

    override fun caretPositionChanged(event: CaretEvent) {
        val editor = event.editor
        val lineNumber = event.newPosition.line
        val startOffset = editor.document.getLineStartOffset(lineNumber)
        val endOffset = editor.document.getLineEndOffset(lineNumber)
        val markupModel: MarkupModel = editor.markupModel

        if (event.oldPosition.line != event.newPosition.line) {
            currentHighlighter?.let {
                editor.markupModel.removeHighlighter(it)
            }
            markupModel.apply {
                val highlighter = addRangeHighlighter(
                    startOffset,
                    endOffset,
                    HighlighterLayer.CARET_ROW,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                currentHighlighter = highlighter
                highlighter.gutterIconRenderer = createGutterIconRenderer(editor)
            }
        }
    }
}
