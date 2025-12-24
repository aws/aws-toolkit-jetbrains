// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import software.amazon.q.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_BUTTON_BORDER
import software.aws.toolkits.resources.AmazonQBundle.message
import java.awt.Point

class InlineChatPopupFactory(
    private val submitHandler: suspend (String, String, Int, Editor) -> String,
    private val acceptHandler: () -> Unit,
    private val rejectHandler: () -> Unit,
    private val cancelHandler: () -> Unit,
    private val popupBufferHeight: Int = 150,
) : Disposable {

    private fun getSelectedText(editor: Editor): String = ReadAction.compute<String, Throwable> {
        val selectionStartOffset = editor.selectionModel.selectionStart
        val selectionEndOffset = editor.selectionModel.selectionEnd
        if (selectionEndOffset > selectionStartOffset) {
            val selectionLineStart = editor.document.getLineStartOffset(editor.document.getLineNumber(selectionStartOffset))
            val selectionLineEnd = editor.document.getLineEndOffset(editor.document.getLineNumber(selectionEndOffset))
            editor.document.getText(TextRange(selectionLineStart, selectionLineEnd))
        } else {
            ""
        }
    }

    private fun getSelectionStartLine(editor: Editor): Int = ReadAction.compute<Int, Throwable> {
        editor.document.getLineNumber(editor.selectionModel.selectionStart)
    }

    fun createPopup(editor: Editor, scope: CoroutineScope): JBPopup {
        val popupPanel = InlineChatPopupPanel(this).apply {
            border = IdeBorderFactory.createRoundedBorder(10).apply {
                setColor(POPUP_BUTTON_BORDER)
            }

            val submitListener: () -> Unit = {
                val prompt = textField.text
                if (prompt.isNotBlank()) {
                    submitButton.isEnabled = false
                    cancelButton.isEnabled = false
                    textField.isEnabled = false
                    setLabel(message("amazonqInlineChat.popup.generating"))
                    revalidate()

                    scope.launch {
                        val selectedCode = getSelectedText(editor)
                        val selectedLineStart = getSelectionStartLine(editor)
                        var errorMessage = ""
                        runBlocking {
                            errorMessage = submitHandler(prompt, selectedCode, selectedLineStart, editor)
                        }
                        if (errorMessage.isNotEmpty()) {
                            withContext(EDT) {
                                setErrorMessage(errorMessage)
                                revalidate()
                            }
                        } else {
                            val acceptAction = {
                                acceptHandler.invoke()
                            }
                            val rejectAction = {
                                rejectHandler.invoke()
                            }
                            withContext(EDT) {
                                addCodeActionsPanel(acceptAction, rejectAction)
                                revalidate()
                            }
                        }
                    }
                }
            }
            setSubmitClickListener(submitListener)
        }
        val popup = initPopup(popupPanel)
        showPopupInEditor(popup, popupPanel, editor)

        return popup
    }

    private fun showPopupInEditor(popup: JBPopup, popupPanel: InlineChatPopupPanel, editor: Editor) {
        val selectionEnd = editor.selectionModel.selectionEnd
        val selectionStart = editor.selectionModel.selectionStart
        val preferredXY = editor.offsetToXY(selectionStart)
        val visibleArea = editor.scrollingModel.visibleArea
        val isBelow = preferredXY.y - visibleArea.y < popupBufferHeight
        val xOffset = getLineByVisualStart(editor, selectionStart)
        val preferredX = editor.contentComponent.locationOnScreen.x + xOffset

        if (isBelow) {
            val offsetXY = editor.offsetToXY(selectionEnd)
            val point = Point(preferredX, offsetXY.y - visibleArea.y + popupBufferHeight)
            popup.show(RelativePoint(point))
        } else {
            val popupXY = Point(preferredX, preferredXY.y - visibleArea.y - editor.lineHeight)
            popup.show(RelativePoint(popupXY))
        }

        popupPanel.textField.requestFocusInWindow()
        popupPanel.textField.addActionListener { e ->
            val inputText = popupPanel.textField.text.trim()
            if (inputText.isNotEmpty()) {
                popupPanel.submitButton.doClick()
            }
        }
    }

    private fun getIndentationForLine(editor: Editor, lineNumber: Int): Int {
        val document = editor.document
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

        // Find the index of the first non-whitespace character
        val firstNonWhitespace = lineText.indexOfFirst { !it.isWhitespace() }

        return if (firstNonWhitespace == -1) {
            0
        } else {
            firstNonWhitespace + 1
        }
    }

    private fun getLineByVisualStart(editor: Editor, offset: Int): Int {
        val visualPosition = editor.offsetToVisualPosition(offset)
        val line = visualPosition.line
        val column = getIndentationForLine(editor, line)
        val lineStartPosition = VisualPosition(line, column)
        return editor.visualToLogicalPosition(lineStartPosition).line
    }

    private fun initPopup(panel: InlineChatPopupPanel): JBPopup {
        val cancelButton = IconButton(message("amazonqInlineChat.popup.cancel"), AllIcons.Actions.Cancel)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.textField)
            .setMovable(true)
            .setResizable(true)
            .setTitle(message("amazonqInlineChat.popup.title"))
            .setCancelButton(cancelButton)
            .setCancelCallback {
                cancelHandler.invoke()
                true
            }
            .setShowBorder(true)
            .setCancelOnWindowDeactivation(false)
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
