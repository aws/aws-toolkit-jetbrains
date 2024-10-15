// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import icons.AwsIcons
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_BUTTON_BORDER
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants

class InlineChatPopupPanel(private val parentDisposable: Disposable) : JPanel() {
    private var submitClickListener: (() -> Unit)? = null

    val textField = JTextField().apply {
        val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
        preferredSize = Dimension(550, 35)
        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(POPUP_BUTTON_BORDER)
        }
        font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
    }

    val submitButton = createButtonWithIcon(AwsIcons.Resources.InlineChat.CONFIRM, "Confirm")

    private val cancelButton = createButtonWithIcon(AwsIcons.Resources.InlineChat.CANCEL, "Cancel").apply {
        addActionListener { Disposer.dispose(parentDisposable) }
    }

    private val buttonsPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
//        preferredSize = Dimension(180, 40)
        add(submitButton, BorderLayout.WEST)
        add(cancelButton, BorderLayout.EAST)
    }

    private val acceptButton = JButton("Accept").apply {
        preferredSize = Dimension(80, 30)
        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(POPUP_BUTTON_BORDER)
        }
    }
    private val rejectButton = JButton("Reject").apply {
        preferredSize = Dimension(80, 30)
        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(POPUP_BUTTON_BORDER)
        }
    }
//    private var submitClickListener: (() -> Unit)? = null
    private val textLabel = JLabel("").apply {
        val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
            font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
//        font = font.deriveFont(POPUP_INFO_TEXT_SIZE)
    }
    private val actionsPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(0, 20, 5, 20)
        add(acceptButton, BorderLayout.WEST)
        add(rejectButton, BorderLayout.EAST)
    }
    private val inputPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 5, 10)
        preferredSize = Dimension(600, 50)
//        maximumSize = Dimension(580, 50)
    }
    private val labelPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 20, 5, 20)
        add(textLabel, BorderLayout.CENTER)
    }

    private val logoPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
//        preferredSize = Dimension(180, 40)
        val logoLabel = JLabel("Edit Code", AwsIcons.Logos.AWS_Q_GREY, SwingConstants.RIGHT).apply {
            font = font.deriveFont(14f)
        }
        add(logoLabel, BorderLayout.CENTER)
    }

    private val bottomPanel = JPanel(BorderLayout()).apply {
        add(logoPanel, BorderLayout.WEST)
        add(buttonsPanel, BorderLayout.EAST)
//        preferredSize = Dimension(650, 40)
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(600, 90)
    }

    private fun createButtonWithIcon(icon: Icon, text: String): JButton {
        return JButton(text).apply {
            horizontalTextPosition = SwingConstants.LEFT
            preferredSize = Dimension(80, 30)
            setIcon(icon)
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            font = font.deriveFont(14f)
        }
    }

    init {
        layout = BorderLayout()
        inputPanel.add(textField, BorderLayout.CENTER)
        inputPanel.preferredSize = Dimension(600, 50)
        add(inputPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        submitButton.addActionListener {
            submitClickListener?.invoke()
        }
    }

    fun setSubmitClickListener(listener: () -> Unit) {
        submitClickListener = listener
    }

    fun setCancelClickListener(listener: () -> Unit) {
        cancelButton.addActionListener { listener.invoke() }
    }

    private fun addActionListener(id: String, action: EditorActionHandler) {
        val actionManager = EditorActionManager.getInstance()
        val originalHandler = actionManager.getActionHandler(id)

        actionManager.setActionHandler(id, action)
        val restorer = Disposable { actionManager.setActionHandler(id, originalHandler) }
        Disposer.register(parentDisposable, restorer)
    }

    private fun getEditorActionHandler(action: () -> Unit) : EditorActionHandler {
        val  handler = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
                action.invoke()
                Disposer.dispose(parentDisposable)
            }
        }
        return handler
    }

    fun addCodeActionsPanel(acceptAction: () -> Unit, rejectAction: () -> Unit ) {
        textLabel.text = "Code diff generated. Do you want to accept it?"
        textLabel.revalidate()
        inputPanel.revalidate()
        acceptButton.addActionListener { acceptAction.invoke() }
        rejectButton.addActionListener { rejectAction.invoke() }
        add(actionsPanel, BorderLayout.SOUTH)
        val enterHandler = getEditorActionHandler(acceptAction)
        addActionListener(IdeActions.ACTION_EDITOR_ENTER, enterHandler)

        val escapeHandler = getEditorActionHandler(rejectAction)
        addActionListener(IdeActions.ACTION_EDITOR_ESCAPE, escapeHandler)
        revalidate()
    }

    fun setLabel(text: String) {
        textLabel.text = text
        textLabel.revalidate()
        remove(inputPanel)
        remove(bottomPanel)
        add(labelPanel)
        revalidate()
    }
}
