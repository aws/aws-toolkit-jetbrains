// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.ui.LightweightHint
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import icons.AwsIcons
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JPanel


class InlineChatEditorHint(private val project: Project, private val editor: Editor) {
    private var hint: LightweightHint? = null

    fun show(location: Point) {
        if (hint != null) return

        val icon = AwsIcons.Logos.AWS_Q_GREY

        val component = HintUtil.createInformationComponent()
        component.isIconOnTheRight = false;
        component.icon = icon
        val coloredText =
            SimpleColoredText(hintText(), SimpleTextAttributes.REGULAR_ATTRIBUTES)

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

        hint = LightweightHint(panel)

        HintManagerImpl.getInstanceImpl().showEditorHint(
            hint!!, editor, location,
            HintManager.HIDE_BY_TEXT_CHANGE,
            0, false,
            HintManagerImpl.createHintHint(editor, location, hint!!, HintManager.RIGHT_UNDER).setContentActive(false)
        )


    }

    fun hide() {
        hint?.hide()
        hint = null
    }


    private fun hintText(): String {
        return "Edit"
    }
}




