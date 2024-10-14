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
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_BUTTON_BORDER
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.POPUP_INFO_TEXT_SIZE
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class InlineChatPopupPanel : JPanel(), Disposable {
    val textField = JTextField().apply {
        val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(POPUP_BUTTON_BORDER)
        }
        font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
    }
    val submitButton = JButton("Send").apply {
        border = IdeBorderFactory.createRoundedBorder().apply {
            setColor(POPUP_BUTTON_BORDER)
        }
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
    private var textChangeListener: ((String) -> Unit)? = null
    private var submitClickListener: (() -> Unit)? = null
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
        border = BorderFactory.createEmptyBorder(12, 10, 12, 10)
        maximumSize = Dimension(580, 30)
    }
    private val labelPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(5, 20, 5, 20)
        add(textLabel, BorderLayout.CENTER)
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(600, 60)
    }

    init {
        layout = BorderLayout()
        inputPanel.add(submitButton, BorderLayout.EAST)
        inputPanel.add(textField, BorderLayout.WEST)
        textField.preferredSize = Dimension(500, 30)
        submitButton.preferredSize = Dimension(60, 30)
        inputPanel.preferredSize = Dimension(600, 30)
        add(inputPanel, BorderLayout.CENTER)
        val listener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                updateText()
            }

            override fun removeUpdate(e: DocumentEvent) {
                updateText()
            }

            override fun changedUpdate(e: DocumentEvent) {
                updateText()
            }

            private fun updateText() {
                val newText = textField.text
                textChangeListener?.invoke(newText)
            }
        }
        textField.document.addDocumentListener(listener)

        submitButton.addActionListener {
            submitClickListener?.invoke()
        }
    }

    fun setTextChangeListener(listener: (String) -> Unit) {
        textChangeListener = listener
    }

    fun setSubmitClickListener(listener: () -> Unit) {
        submitClickListener = listener
    }

    private fun addActionListener(id: String, action: EditorActionHandler) : Disposable {
        val actionManager = EditorActionManager.getInstance()
        val originalHandler = actionManager.getActionHandler(id)

        actionManager.setActionHandler(id, action)
        val restorer = Disposable { actionManager.setActionHandler(id, originalHandler) }
        return restorer
    }

    fun addCodeActionsPanel(acceptAction: () -> Unit, rejectAction: () -> Unit ) {
        textLabel.text = "Code diff generated. Do you want to accept it?"
        textLabel.revalidate()
        inputPanel.revalidate()
        acceptButton.addActionListener { acceptAction.invoke() }
        rejectButton.addActionListener { rejectAction.invoke() }
        add(actionsPanel, BorderLayout.SOUTH)
        val enterHandler = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
                acceptAction.invoke()
                Disposer.dispose(this@InlineChatPopupPanel)
            }
        }

        val enterRestorer = addActionListener(IdeActions.ACTION_EDITOR_ENTER, enterHandler)
        Disposer.register(this, enterRestorer)

        val escapeHandler = object : EditorActionHandler() {
            override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
                rejectAction.invoke()
                Disposer.dispose(this@InlineChatPopupPanel)
            }
        }

        val escapeRestorer = addActionListener(IdeActions.ACTION_EDITOR_ESCAPE, escapeHandler)
        Disposer.register(this, escapeRestorer)
        revalidate()
    }

    fun setLabel(text: String) {
        textLabel.text = text
        textLabel.revalidate()
        remove(inputPanel)
        add(labelPanel)
        revalidate()
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }
}
