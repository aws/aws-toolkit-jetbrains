// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
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
    private val HINT_LOCATION_BUFFER = 50

    private fun getHintLocation (editor: Editor): Point {
        val selectionModel = editor.selectionModel
        val lineSelected = selectionModel.selectedText?.split("\n")?.size
        val offset: Int = if (lineSelected != null && lineSelected > 1) {(lineSelected - 1).times(editor.lineHeight)} else {0}
        val bestPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor).point
        val visibleArea = editor.scrollingModel.visibleArea

        val adjustedX = (bestPosition.x + 200).coerceAtMost(visibleArea.x + visibleArea.width - HINT_LOCATION_BUFFER)
        val adjustedY = (bestPosition.y + offset).coerceAtMost(visibleArea.y + visibleArea.height - HINT_LOCATION_BUFFER)
        val adjustedPosition = Point(adjustedX, adjustedY)

        return adjustedPosition
    }

    private fun createHint ():  LightweightHint{
        val icon = AwsIcons.Logos.AWS_Q_GREY

        val component = HintUtil.createInformationComponent()
        component.isIconOnTheRight = false;
        component.icon = icon
        val coloredText =
            SimpleColoredText(message("amazonqInlineChat.hint.edit"), SimpleTextAttributes.REGULAR_ATTRIBUTES)

        coloredText.appendToComponent(component)
        val shortcutComponent = HintUtil.createInformationComponent()
        if (!SystemInfo.isWindows) {
            val shortCutIcon = AwsIcons.Resources.InlineChat.AWS_Q_INLINECHAT_SHORTCUT
            shortcutComponent.isIconOnTheRight = true;
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
            hint, editor, location,
            HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
            0, false,
            HintManagerImpl.createHintHint(editor, location, hint, HintManager.RIGHT_UNDER).setContentActive(false)
        )
    }

    fun hide() {
        hint.hide()
    }
}




