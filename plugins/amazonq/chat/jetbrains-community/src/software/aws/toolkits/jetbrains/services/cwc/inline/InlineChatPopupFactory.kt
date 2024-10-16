// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.ui.IdeBorderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_BUTTON_BORDER


class InlineChatPopupFactory(
    private val editor: Editor,
    private val submitHandler: suspend (String, String, Int, Editor) -> String,
    private val acceptHandler: () -> Unit,
    private val rejectHandler: () -> Unit,
    private val cancelHandler: () -> Unit,
) : Disposable {

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

    fun createPopup(scope: CoroutineScope): JBPopup {
        val popupPanel = InlineChatPopupPanel(this).apply {
            border = IdeBorderFactory.createRoundedBorder(10).apply {
                setColor(POPUP_BUTTON_BORDER)
            }

            val submitListener: () -> Unit = {
                submitButton.isEnabled = false
                textField.isEnabled = false
                val prompt = textField.text
                if (prompt.isNotBlank()) {
                    setLabel("Generating...")
                    revalidate()

                    scope.launch {
                        val selectedCode = getSelectedText(editor)
                        val selectedLineStart = getSelectionStartLine(editor)
                        var errorMessage = ""
                        runBlocking {
                            errorMessage = submitHandler(prompt, selectedCode, selectedLineStart, editor)
                        }
                        if (errorMessage.isNotEmpty()) {
                            setErrorMessage(errorMessage)
                            revalidate()
                        } else {
                            val acceptAction = {
                                acceptHandler.invoke()
                            }
                            val rejectAction = {
                                rejectHandler.invoke()
                            }
                            addCodeActionsPanel(acceptAction , rejectAction)
                        }
                    }
                }
            }
            setSubmitClickListener(submitListener)
        }
        val popup = initPopup(popupPanel)
        val popupPoint = editor.visualPositionToXY(editor.caretModel.currentCaret.visualPosition)
        popup.setLocation(popupPoint)
        popup.showInBestPositionFor(editor)
        popupPanel.textField.requestFocusInWindow()
        popupPanel.textField.addActionListener { e ->
            val inputText = popupPanel.textField.text.trim()
            if (inputText.isNotEmpty()) {
                popupPanel.submitButton.doClick()
            }
        }
        return popup
    }

    private fun initPopup(panel: InlineChatPopupPanel): JBPopup {
        val cancelButton = IconButton("Cancel", AllIcons.Actions.Cancel)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.textField)
            .setMovable(true)
            .setResizable(true)
            .setTitle("Enter Instructions for Q")
            .setCancelButton(cancelButton)
            .setCancelCallback {
                cancelHandler.invoke()
                true
            }
            .setShowBorder(true)
            .setCancelOnWindowDeactivation(false)
            .setAlpha(0.2F)
            .setCancelOnClickOutside(false)
            .setCancelOnOtherWindowOpen(false)
            .setFocusable(true)
            .setRequestFocus(true)
            .setLocateWithinScreenBounds(true)
            .createPopup()
        return popup
    }

    override fun dispose() {
        cancelHandler.invoke()
    }
}

