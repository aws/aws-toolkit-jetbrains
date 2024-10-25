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
import software.aws.toolkits.resources.AmazonQBundle.message
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
    private val popupButtonFontSize = 14f
    val popupWidth = 600
    val popupHeight = 90
    private val popupButtonHeight = 30
    private val popupButtonWidth = 80
    private val popupInputHeight = 40
    private val popupInputWidth = 500

    val textField = createTextField()

    val submitButton = createButtonWithIcon(AwsIcons.Resources.InlineChat.CONFIRM, message("amazonqInlineChat.popup.confirm"))

    val cancelButton = createButtonWithIcon(AwsIcons.Resources.InlineChat.REJECT, message("amazonqInlineChat.popup.cancel")).apply {
        addActionListener {
            if (!Disposer.isDisposed(parentDisposable)) {
                Disposer.dispose(parentDisposable)
            }
        }
    }

    private val buttonsPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        add(submitButton, BorderLayout.WEST)
        add(cancelButton, BorderLayout.EAST)
    }

    private val acceptButton = createButtonWithIcon(AwsIcons.Resources.InlineChat.CONFIRM, message("amazonqInlineChat.popup.accept"))
    private val rejectButton = createButtonWithIcon(AwsIcons.Resources.InlineChat.REJECT, message("amazonqInlineChat.popup.reject"))
    private val textLabel = JLabel(message("amazonqInlineChat.popup.editCode"), AwsIcons.Logos.AWS_Q_GREY, SwingConstants.RIGHT).apply {
        font = font.deriveFont(popupButtonFontSize)
    }

    private val inputPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 5, 10)
    }

    private val logoPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        add(textLabel, BorderLayout.CENTER)
    }

    private val bottomPanel = JPanel(BorderLayout()).apply {
        add(logoPanel, BorderLayout.WEST)
        add(buttonsPanel, BorderLayout.EAST)
    }

    override fun getPreferredSize(): Dimension = Dimension(popupWidth, popupHeight)

    private fun createTextField(): JTextField = JTextField().apply {
        val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
        preferredSize = Dimension(popupInputWidth, popupInputHeight)
        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(POPUP_BUTTON_BORDER)
        }
        font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
    }

    private fun createButtonWithIcon(icon: Icon, text: String): JButton = JButton(text).apply {
        horizontalTextPosition = SwingConstants.LEFT
        preferredSize = Dimension(popupButtonWidth, popupButtonHeight)
        setIcon(icon)
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        font = font.deriveFont(popupButtonFontSize)
    }

    init {
        layout = BorderLayout()
        inputPanel.add(textField, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        submitButton.addActionListener {
            submitClickListener?.invoke()
        }
    }

    fun setSubmitClickListener(listener: () -> Unit) {
        submitClickListener = listener
    }

    private fun addActionListener(id: String, action: EditorActionHandler) {
        val actionManager = EditorActionManager.getInstance()
        val originalHandler = actionManager.getActionHandler(id)

        actionManager.setActionHandler(id, action)
        val restorer = Disposable { actionManager.setActionHandler(id, originalHandler) }
        Disposer.register(parentDisposable, restorer)
    }

    private fun getEditorActionHandler(action: () -> Unit): EditorActionHandler = object : EditorActionHandler() {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
            action.invoke()
            Disposer.dispose(parentDisposable)
        }
    }

    fun addCodeActionsPanel(acceptAction: () -> Unit, rejectAction: () -> Unit) {
        textLabel.text = message("amazonqInlineChat.popup.editCode")
        // this is a workaround somehow the textField will interfere with the enter handler
        val emptyTextField = createTextField()
        emptyTextField.text = textField.text
        emptyTextField.isEnabled = false
        inputPanel.remove(textField)
        inputPanel.add(emptyTextField, BorderLayout.CENTER)

        buttonsPanel.remove(submitButton)
        buttonsPanel.remove(cancelButton)
        buttonsPanel.add(acceptButton, BorderLayout.WEST)
        buttonsPanel.add(rejectButton, BorderLayout.EAST)
        acceptButton.addActionListener { acceptAction.invoke() }
        rejectButton.addActionListener { rejectAction.invoke() }

        val enterHandler = getEditorActionHandler(acceptAction)
        addActionListener(IdeActions.ACTION_EDITOR_ENTER, enterHandler)

        val escapeHandler = getEditorActionHandler(rejectAction)
        addActionListener(IdeActions.ACTION_EDITOR_ESCAPE, escapeHandler)
        revalidate()
    }

    fun setLabel(text: String) {
        textLabel.text = text
        textField.isEnabled = false
        revalidate()
    }
}