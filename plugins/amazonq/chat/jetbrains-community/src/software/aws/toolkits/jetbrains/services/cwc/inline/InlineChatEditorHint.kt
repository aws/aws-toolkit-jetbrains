// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.ui.LightweightHint
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import icons.AwsIcons
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JPanel


class InlineChatEditorHint {
    private val hint = createHint()

    private fun getHintLocation (editor: Editor): Point {
        val selectionModel = editor.selectionModel
        val selectionEnd = selectionModel.selectionEnd
        val selectionLineEnd = editor.document.getLineEndOffset(editor.document.getLineNumber(selectionEnd))

        val xyPosition = editor.offsetToXY(selectionLineEnd)
        val editorLocation = editor.component.locationOnScreen
        val editorContentLocation = editor.contentComponent.locationOnScreen
        val position = Point(
            editorContentLocation.x + xyPosition.x,
            editorLocation.y + xyPosition.y - editor.scrollingModel.verticalScrollOffset - 50)

        val visibleArea = editor.scrollingModel.visibleArea

        val adjustedX = (position.x ).coerceAtMost(visibleArea.x + visibleArea.width - 50)
        val adjustedY = (position.y ).coerceAtMost(visibleArea.y + visibleArea.height - 50)
        val adjustedPosition = Point(adjustedX, adjustedY)

        return adjustedPosition
    }

    private fun createHint ():  LightweightHint{
        val icon = AwsIcons.Logos.AWS_Q_GREY

        val component = HintUtil.createInformationComponent()
        component.isIconOnTheRight = false;
        component.icon = icon
        val coloredText =
            SimpleColoredText("Edit", SimpleTextAttributes.REGULAR_ATTRIBUTES)

        val shortCutIcon = AwsIcons.Misc.AWS_Q_INLINECHAT_SHORTCUT
        val shortcutComponent = HintUtil.createInformationComponent()
        shortcutComponent.isIconOnTheRight = true;
        shortcutComponent.icon = shortCutIcon

        coloredText.appendToComponent(shortcutComponent)

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
            hint, editor, location,
            HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
            0, false,
            HintManagerImpl.createHintHint(editor, location, hint!!, HintManager.RIGHT_UNDER).setContentActive(false)
        )
    }

    fun hide() {
        hint.hide()
    }
}




