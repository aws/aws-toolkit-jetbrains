// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.messages.AmazonQMessage
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessageConnector
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AMAZON_Q_WINDOW_ID
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.amazonq.webview.BrowserConnector
import software.aws.toolkits.jetbrains.services.codewhisperer.inlay.CodeWhispererInlayInlineRenderer
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupListener
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners.CodeWhispererScrollListener
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_BUTTON_BORDER
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_DIM_HEX
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_HOVER
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.POPUP_PANEL_SEPARATOR
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.POPUP_BUTTON_TEXT_SIZE
import software.aws.toolkits.jetbrains.services.cwc.commands.ActionRegistrar
import software.aws.toolkits.jetbrains.services.cwc.commands.ContextMenuActionMessage
import software.aws.toolkits.jetbrains.services.cwc.commands.EditorContextCommand
import software.aws.toolkits.jetbrains.services.cwc.messages.EditorContextCommandMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.IncomingCwcMessage
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ChatInputInlay(
    private val editor: Editor,
    private val position: LogicalPosition,
    private val context: AmazonQAppInitContext
) : Disposable {
    private var inlay: Inlay<EditorCustomElementRenderer>? = null
    private var currentPopup: JBPopup? = null

    init {
        createPopup()
    }

    private fun createPopup() {
        val popupPanel = ChatInputPopupPanel().apply {
            setTextChangeListener {
                println("Text changed: $it")
            }

            setSubmitClickListener {
                val text = textField.text
                ActionRegistrar.instance.reportMessageClick(EditorContextCommand.SendToChat, context.project, text)
//                println("Submitted text: $text")
                hidePopup()
                ToolWindowManager.getInstance(context.project).getToolWindow(AMAZON_Q_WINDOW_ID)?.activate(null, true)
            }
        }
        val popup = initPopup(popupPanel)
        val popupPoint = editor.visualPositionToXY(editor.caretModel.currentCaret.visualPosition)
        popup.setLocation(popupPoint)
        popup.showInBestPositionFor(editor)
        popupPanel.textField.requestFocusInWindow()
        currentPopup = popup
    }

//    private fun createInlay() {
//        val renderer = object : EditorCustomElementRenderer {
//            override fun calcWidthInPixels(inlay: Inlay<*>): Int {
//                return 500
//            }
//
//            override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
////                val point = editor.logicalPositionToXY(position)
//                val attributes = editor.colorsScheme.getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE) ?: return
//                val fgColor = attributes.foregroundColor ?: return
//
//                val popupPanel = ChatInputPopupPanel().apply {
////                    add(acceptButton)
//                    setTextChangeListener {
//                        println("Text changed: $it")
//                    }
//
//                    setSubmitClickListener {
//                        val text = textField.text
//                        ActionRegistrar.instance.reportMessageClick(EditorContextCommand.SendToChat, context.project, text)
//                        println("Submitted text: $text")
//                        hidePopup()
//                    }
//                }
//                val popup = initPopup(popupPanel)
//                val popupPoint = editor.visualPositionToXY(editor.caretModel.currentCaret.visualPosition)
//                popup.setLocation(popupPoint)
//                popup.showInBestPositionFor(editor)
//                popupPanel.textField.requestFocusInWindow()
//                currentPopup = popup
//            }
//        }
//        val inlayModel = editor.inlayModel
//        inlay = inlayModel.addBlockElement(
//            position.line,
//            true,
//            true,
//            0,
//            renderer
//        )
//    }

    fun hidePopup() {
        currentPopup?.dispose()
        currentPopup = null
    }

    private fun initPopup(panel: ChatInputPopupPanel): JBPopup {
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.textField)
//            .setMovable(true)
//            .setResizable(true)
            .setTitle("Ask Amazon Q")
            .setAlpha(0.2F)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setCancelKeyEnabled(true)
            .setFocusable(true)
            .setRequestFocus(true)
            .setLocateWithinScreenBounds(true)
            .setCancelOnWindowDeactivation(true)
            .createPopup()
        return popup
    }

    class ChatInputPopupPanel : JPanel() {
        val textField = JTextField(50)
        private val submitButton = JButton("Confirm")
        private var textChangeListener: ((String) -> Unit)? = null
        private var submitClickListener: (() -> Unit)? = null


        init {
            layout = BorderLayout()
            val inputPanel = JPanel(BorderLayout())
            inputPanel.add(textField, BorderLayout.WEST)
            submitButton.preferredSize = Dimension(80, 30)
            inputPanel.add(submitButton, BorderLayout.EAST)
            add(inputPanel, BorderLayout.NORTH)
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
            // Add a document listener to the text field
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
    }

    override fun dispose() {
        hidePopup()
    }
}

