// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.LightweightHint
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import icons.AwsIcons
import software.aws.toolkits.resources.AmazonQBundle.message
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JPanel

class InlineChatEditorHint {
    private val hint = createHint()

    private fun getHintLocation(editor: Editor): Point {
        val range = editor.calculateVisibleRange()
        val document = editor.document
        val selectionEnd = editor.selectionModel.selectionEnd
        val isOneLineSelection = isOneLineSelection(editor)
        val isBelow = editor.offsetToXY(selectionEnd) !in editor.scrollingModel.visibleArea
        val areEdgesOutsideOfVisibleArea = editor.selectionModel.selectionStart !in range && editor.selectionModel.selectionEnd !in range
        val offsetForHint = when {
            isOneLineSelection -> selectionEnd
            areEdgesOutsideOfVisibleArea -> document.getLineEndOffset(getLineByVisualStart(editor, editor.caretModel.offset, true))
            isBelow -> document.getLineEndOffset(getLineByVisualStart(editor, selectionEnd, true))
            else -> document.getLineEndOffset(getLineByVisualStart(editor, selectionEnd, false))
        }
        val visualPosition = editor.offsetToVisualPosition(offsetForHint)
        val hintPoint = HintManagerImpl.getHintPosition(hint, editor, visualPosition, HintManager.RIGHT)
        hintPoint.translate(0, if (isBelow) editor.lineHeight else 0)
        return hintPoint
    }

    private fun isOneLineSelection(editor: Editor): Boolean {
        val document = editor.document
        val selectionModel = editor.selectionModel
        val startLine = document.getLineNumber(selectionModel.selectionStart)
        val endLine = document.getLineNumber(selectionModel.selectionEnd)
        return startLine == endLine
    }

    private fun getLineByVisualStart(editor: Editor, offset: Int, skipLineStartOffset: Boolean): Int {
        val visualPosition = editor.offsetToVisualPosition(offset)
        val skipCurrentLine = skipLineStartOffset && visualPosition.column == 0
        val line = if (skipCurrentLine) maxOf(visualPosition.line - 1, 0) else visualPosition.line
        val lineStartPosition = VisualPosition(line, 0)
        return editor.visualToLogicalPosition(lineStartPosition).line
    }

    private fun createHint(): LightweightHint {
        val icon = AwsIcons.Logos.AWS_Q_GREY

        val component = HintUtil.createInformationComponent()
        component.isIconOnTheRight = false
        component.icon = icon
        val coloredText =
            SimpleColoredText(message("amazonqInlineChat.hint.edit"), SimpleTextAttributes.REGULAR_ATTRIBUTES)

        coloredText.appendToComponent(component)
        val shortcutComponent = HintUtil.createInformationComponent()
        if (!SystemInfo.isWindows) {
            val shortCutIcon = AwsIcons.Resources.InlineChat.AWS_Q_INLINECHAT_SHORTCUT
            shortcutComponent.isIconOnTheRight = true
            shortcutComponent.icon = shortCutIcon
        } else {
            val shortcutText =
                SimpleColoredText(message("amazonqInlineChat.hint.windows.shortCut"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            shortcutText.appendToComponent(shortcutComponent)
        }

        val panel = JPanel(BorderLayout()).apply {
            add(component, BorderLayout.WEST)
            add(shortcutComponent, BorderLayout.EAST)
            isOpaque = true
            background = component.background
            revalidate()
            repaint()
        }

        return LightweightHint(panel)
    }

    fun show(editor: Editor) {
        val location = getHintLocation(editor)
        HintManagerImpl.getInstanceImpl().showEditorHint(
            hint,
            editor,
            location,
            HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
            0,
            false,
            HintManagerImpl.createHintHint(editor, location, hint, HintManager.RIGHT).setContentActive(false)
        )
    }

    fun hide() {
        hint.hide()
    }
}
