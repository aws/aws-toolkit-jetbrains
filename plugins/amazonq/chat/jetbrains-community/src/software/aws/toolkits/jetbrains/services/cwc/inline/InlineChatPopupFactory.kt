// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.TextRange
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_BUTTON_BORDER
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.POPUP_INFO_TEXT_SIZE
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
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


class InlineChatPopupFactory(
    private val editor: Editor,
    private val submitHandler: suspend (String, String, Int) -> String,
    private val acceptHandler: () -> Unit,
    private val rejectHandler: () -> Unit,
    private val cancelHandler: () -> Unit,
    private val telemetryHelper: TelemetryHelper,
    private val scope: CoroutineScope
) {

    private fun getSelectedText(editor: Editor): String {
        return ReadAction.compute<String, Throwable> {
            val selectionStartOffset = editor.selectionModel.selectionStart
            val selectionEndOffset = editor.selectionModel.selectionEnd
            if (selectionEndOffset > selectionStartOffset) {
                val selectionLineStart = editor.document.getLineStartOffset(editor.document.getLineNumber(selectionStartOffset))
                val selectionLineEnd = editor.document.getLineEndOffset(editor.document.getLineNumber(selectionEndOffset))
                editor.document.getText(TextRange(selectionLineStart, selectionLineEnd))
            } else ""
        }
    }

    private fun getSelectionStartLine(editor: Editor): Int {
        return ReadAction.compute<Int, Throwable> {
            editor.document.getLineNumber(editor.selectionModel.selectionStart)
        }
    }

    fun createPopup(): JBPopup {
        val popupPanel = ChatInputPopupPanel().apply {
            border = IdeBorderFactory.createRoundedBorder(10).apply {
                setColor(POPUP_BUTTON_BORDER)
            }

            setSubmitClickListener {
                submitButton.isEnabled = false
                textField.isEnabled = false
                val requestStartTime = System.currentTimeMillis()
                val prompt = textField.text
                setLabel("Waiting for Amazon Q...")
                revalidate()
                DaemonCodeAnalyzer.getInstance(editor.project).restart()

                scope.launch {
                    val selectedCode = getSelectedText(editor)
                    val selectedLineStart = getSelectionStartLine(editor)
                    val numOfLinesSelected = selectedCode.split("\n").size
                    var errorMessage = ""
                    runBlocking {
                        errorMessage = submitHandler(prompt, selectedCode, selectedLineStart)
                    }
                    val requestEndLatency = (System.currentTimeMillis() - requestStartTime).toDouble()
//                    withContext(EDT) {
//                        if (!isVisible) {
//                            cancelHandler.invoke()
//                            return@launch
//                        }
                        if (errorMessage.isNotEmpty()) {
                            setLabel(errorMessage)
                            revalidate()
                        } else {

                        val acceptAction = {
                            acceptHandler.invoke()
//                            telemetryHelper.recordInlineChatTelemetry(prompt.length, numOfLinesSelected, true,
//                                InlineChatUserDecision.ACCEPT, 0.0, requestEndLatency)
                        }
                        val rejectAction = {
                            rejectHandler.invoke()
//                            telemetryHelper.recordInlineChatTelemetry(prompt.length, numOfLinesSelected, true,
//                                InlineChatUserDecision.REJECT, 0.0, requestEndLatency)
                        }
                        addCodeActionsPanel(acceptAction , rejectAction)
//                        DaemonCodeAnalyzer.getInstance(editor.project).restart()
//                        }
                    }
                }
            }
        }
        val popup = initPopup(popupPanel)
//        popup.addListener(popupListener)
//        addPopupListeners(popup)
        val popupPoint = editor.visualPositionToXY(editor.caretModel.currentCaret.visualPosition)
        popup.setLocation(popupPoint)
        popup.showInBestPositionFor(editor)
        popupPanel.textField.requestFocusInWindow()
        return popup
    }

    private fun initPopup(panel: ChatInputPopupPanel): JBPopup {
        val titlePanel = JPanel(BorderLayout()).apply {
            background = JBColor.background()
            val titleLabel = JBLabel("Send to AmazonQ").apply {
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
            }
            add(titleLabel, BorderLayout.CENTER)
            val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
            border = IdeBorderFactory.createRoundedBorder().apply {
                setColor(POPUP_BUTTON_BORDER)
            }
            font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
        }
        val cancelButton = IconButton("Cancel", AllIcons.Actions.Cancel).apply {}
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.textField)
            .setMovable(true)
            .setResizable(true)
            .setTitle("Enter Instructions for Q")
            .setCancelButton(cancelButton)
            .setShowBorder(true)
            .setCancelOnWindowDeactivation(false)
            .setAlpha(0.2F)
            .setCancelOnClickOutside(false)
            .setCancelOnOtherWindowOpen(false)
//            .setCancelKeyEnabled(true)
            .setFocusable(true)
            .setRequestFocus(true)
            .setLocateWithinScreenBounds(true)
            .createPopup()
        return popup
    }

    class ChatInputPopupPanel : JPanel() {
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
//            isFocusable = false
            border = IdeBorderFactory.createRoundedBorder().apply {
                setColor(POPUP_BUTTON_BORDER)
            }
        }
        private val rejectButton = JButton("Reject").apply {
            preferredSize = Dimension(80, 30)
//            isFocusable = false
            border = IdeBorderFactory.createRoundedBorder().apply {
                setColor(POPUP_BUTTON_BORDER)
            }
        }
        private var textChangeListener: ((String) -> Unit)? = null
        private var submitClickListener: (() -> Unit)? = null
        private val textLabel = JLabel("").apply {
            val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
//            font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
            font = font.deriveFont(POPUP_INFO_TEXT_SIZE)
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

//        private fun addActionListener(id: String, action: EditorWriteActionHandler) : Disposable {
//            val actionManager = EditorActionManager.getInstance()
//            val originalHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_TAB)
//
//            actionManager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, action)
//            val restorer = object : Disposable {
//                override fun dispose() {
//                    actionManager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, originalHandler)
//                }
//            }
//            return restorer
//        }

        fun addCodeActionsPanel(acceptAction: () -> Unit, rejectAction: () -> Unit ) {
            textLabel.text = "Code diff generated. Do you want to accept it?"
            textLabel.revalidate()
            inputPanel.revalidate()
            acceptButton.addActionListener { acceptAction.invoke() }
            rejectButton.addActionListener { rejectAction.invoke() }
            add(actionsPanel, BorderLayout.SOUTH)
            revalidate()
        }

        fun setLabel(text: String) {
            textLabel.text = text
            textLabel.revalidate()
            remove(inputPanel)
            add(labelPanel)
            revalidate()
        }
    }
}

