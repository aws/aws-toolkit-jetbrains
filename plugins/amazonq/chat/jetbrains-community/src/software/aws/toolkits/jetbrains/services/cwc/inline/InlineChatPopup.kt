// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.v1.ChatSessionFactoryV1
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.messenger.ChatPromptHandler
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.userIntent.UserIntentRecognizer
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionInfo
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionStorage
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class InlineChatPopup(
    private val editor: Editor,
    private val context: AmazonQAppInitContext
) : Disposable {
    private var currentPopup: JBPopup? = null
    private val scope = disposableCoroutineScope(this)
    private var rangeHighlighter: RangeHighlighter? = null
    private var undoAction: (() -> Unit?)? = null

    init {
        createPopup()
    }

    private fun createPopup() {
        val popupListener = object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                undoAction?.invoke()
            }
        }
        val popupPanel = ChatInputPopupPanel().apply {
            setTextChangeListener {
                println("Text changed: $it")
            }

            setSubmitClickListener {
                submitButton.isEnabled = false
                textField.isEnabled = false
                val prompt = textField.text
                setLabel("Waiting for Amazon Q...")
                revalidate()

                scope.launch {
                    val messages = handleChat(prompt)
                    val src = messages.last().message ?: return@launch
                    val codeBlocks = getCodeBlocksRecursively(src)
                    if (codeBlocks.isNotEmpty()) {
                        val codeString = StringUtil.unescapeStringCharacters(extractContentAfterFirstNewline(codeBlocks.first()))
                        withContext(EDT) {
                            val caret: Caret = editor.caretModel.primaryCaret
                            val offset = caret.offset
                            val caretStart = caret.selectionStart
                            val caretEnd = caret.selectionEnd

                            if (caret.hasSelection()) {
                                if(!isVisible){
                                    return@withContext
                                }
                                caret.removeSelection()

                                WriteCommandAction.runWriteCommandAction(context.project) {
                                    editor.document.insertString(offset, codeString)
                                }
                                undoAction = {
                                    WriteCommandAction.runWriteCommandAction(context.project) {
                                        editor.document.deleteString(offset, offset + codeString.length)
                                    }
                                    editor.markupModel.removeAllHighlighters()
                                }
                                highlightCodeWithBackgroundColor(editor, offset, offset + codeString.length, true)
                                highlightCodeWithBackgroundColor(editor, caretStart + codeString.length, caretEnd + codeString.length, false)
                                val acceptAction = {
                                    undoAction = null
                                    WriteCommandAction.runWriteCommandAction(context.project) {
                                        editor.document.deleteString(caretStart + codeString.length, caretEnd + codeString.length)
                                    }
                                    editor.markupModel.removeAllHighlighters()
                                    hidePopup()
                                }
                                val rejectAction = {
                                    WriteCommandAction.runWriteCommandAction(context.project) {
                                        editor.document.deleteString(offset, offset + codeString.length)
                                    }
                                    editor.markupModel.removeAllHighlighters()
                                    hidePopup()
                                }
                                addCodeActionsPanel(acceptAction, rejectAction)
                            }

                        }
                        return@launch
                    }

//                    TODO: change textArea to textPane and render markdown
//                    val flavour = CommonMarkFlavourDescriptor()
//                    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(src)
//                    val html = HtmlGenerator(src, parsedTree, flavour).generateHtml()
                    setLabel(src)
                    textField.isEnabled = true
                    submitButton.isEnabled = true
                    revalidate()
                }
            }
        }
        val popup = initPopup(popupPanel)
        popup.addListener(popupListener)
        val popupPoint = editor.visualPositionToXY(editor.caretModel.currentCaret.visualPosition)
        popup.setLocation(popupPoint)
        popup.showInBestPositionFor(editor)
        popupPanel.textField.requestFocusInWindow()
        currentPopup = popup
    }

    private fun highlightCodeWithBackgroundColor(editor: Editor, startOffset: Int, endOffset: Int, isGreen: Boolean) {

        val greenBackgroundAttributes = TextAttributes().apply {
            backgroundColor = JBColor(0x66BB6A, 0x006400)
            effectColor = JBColor(0x66BB6A, 0x006400)
        }

        val redBackgroundAttributes = TextAttributes().apply {
            backgroundColor = JBColor(0xEF9A9A, 0x8B0000)
            effectColor = JBColor(0xEF9A9A, 0x8B0000)
        }
        val attributes = if (isGreen) greenBackgroundAttributes else redBackgroundAttributes
        rangeHighlighter= editor.markupModel.addRangeHighlighter(
            startOffset, endOffset, HighlighterLayer.ADDITIONAL_SYNTAX,
            attributes, HighlighterTargetArea.EXACT_RANGE
        )
    }

    private fun extractContentAfterFirstNewline(input: String): String {
        val newlineIndex = input.indexOf('\n')
        return if (newlineIndex != -1) {
            input.substring(newlineIndex + 1)
        } else {
            input
        }
    }


    fun hidePopup() {
        currentPopup?.dispose()
        currentPopup = null
    }

    private fun initPopup(panel: ChatInputPopupPanel): JBPopup {
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.textField)
            .setMovable(true)
            .setResizable(true)
            .setTitle("Ask Amazon Q")
            .setAlpha(0.1F)
            .setCancelOnClickOutside(false)
            .setCancelOnOtherWindowOpen(true)
//            .setCancelKeyEnabled(true)
            .setFocusable(true)
            .setRequestFocus(true)
            .setLocateWithinScreenBounds(true)
            .setCancelOnWindowDeactivation(true)
            .createPopup()
        return popup
    }

    class ChatInputPopupPanel : JPanel() {
        val textField = JTextField().apply {
            val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
            font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
        }
        val submitButton = JButton("Send")
        private val acceptButton = JButton("Accept")
        private val rejectButton = JButton("Reject")
        private var textChangeListener: ((String) -> Unit)? = null
        private var submitClickListener: (() -> Unit)? = null
        private val textArea = JTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
            font = Font(editorColorsScheme.editorFontName, Font.PLAIN, editorColorsScheme.editorFontSize)
        }
//            JTextPane().apply {
//            contentType = "text/html"
//            editorKit = HTMLEditorKit()
//            text = initialContent
//            isEditable = false
//        }
        private val scrollPane = JBScrollPane(textArea).apply {
            border = BorderFactory.createEmptyBorder(5, 20, 5, 20)
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        }

        override fun getPreferredSize(): Dimension {
            return Dimension(600, 120)
        }

        init {
            layout = BorderLayout()
                val inputPanel = JPanel(BorderLayout())
                inputPanel.add(submitButton, BorderLayout.EAST)
                inputPanel.add(textField, BorderLayout.WEST)
                textField.preferredSize = Dimension(500, 30)
                submitButton.preferredSize = Dimension(80, 30)
                inputPanel.preferredSize = Dimension(600, 30)
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
                textField.document.addDocumentListener(listener)

                submitButton.addActionListener {
                    submitClickListener?.invoke()
                }
//            }
        }

        fun setTextChangeListener(listener: (String) -> Unit) {
            textChangeListener = listener
        }

        fun setSubmitClickListener(listener: () -> Unit) {
            submitClickListener = listener
        }

        fun addCodeActionsPanel(acceptAction: () -> Unit, rejectAction: () -> Unit) {
            setLabel("Code diff generated. Do you want to accept or reject it?")
            val actionsPanel = JPanel(BorderLayout())
            actionsPanel.border = BorderFactory.createEmptyBorder(5, 20, 5, 20)
            acceptButton.preferredSize = Dimension(80, 30)
            rejectButton.preferredSize = Dimension(80, 30)
            acceptButton.addActionListener { acceptAction.invoke() }
            rejectButton.addActionListener { rejectAction.invoke() }
            actionsPanel.add(acceptButton, BorderLayout.WEST)
            actionsPanel.add(rejectButton, BorderLayout.EAST)
            add(actionsPanel, BorderLayout.SOUTH)
            revalidate()
        }

        fun setLabel(text: String) {
            textArea.text = text
            textArea.revalidate()
            scrollPane.revalidate()
            if (text.isNotEmpty()){
                add(scrollPane, BorderLayout.CENTER)
            }
            revalidate()
        }
    }

    private fun getCodeBlocksRecursively(src: String): List<String> {
        val codeBlocks = mutableListOf<String>()
        var currentIndex = 0

        while (currentIndex < src.length) {
            val startIndex = src.indexOf("```", currentIndex)
            if (startIndex == -1) break

            val endIndex = src.indexOf("```", startIndex + 3)
            if (endIndex == -1) break

            val code = src.substring(startIndex + 3, endIndex)
            codeBlocks.add(code)

            currentIndex = endIndex + 3
        }

        return codeBlocks
    }


    private suspend fun handleChat (message: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val triggerId = UUID.randomUUID().toString()
        val chatSessionStorage = ChatSessionStorage(ChatSessionFactoryV1())
        val telemetryHelper = TelemetryHelper(context, chatSessionStorage)
        val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = context.fqnWebviewAdapter, project = context.project)
        val intentRecognizer = UserIntentRecognizer()

        val prompt = if (intentRecognizer.getUserIntentFromPromptChatMessage(message, true) != UserIntent.EXPLAIN_CODE_SELECTION) {
            "$message. Please only include code blocks in your response."
        } else {
            "$message. Please do not include code blocks in your response."
        }

        val requestData = ChatRequestData(
            tabId = "inlineChat-editor",
            message = prompt,
            activeFileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ChatMessage),
            userIntent = intentRecognizer.getUserIntentFromPromptChatMessage(message, true),
            triggerType = TriggerType.Click,
            customization = CodeWhispererModelConfigurator.getInstance().activeCustomization(context.project),
            relevantTextDocuments = emptyList()
        )
        val session = ChatSessionFactoryV1().create(context.project)
        val sessionInfo = ChatSessionInfo(session = session, scope = scope, history = mutableListOf())
        val chat = sessionInfo.scope.async { ChatPromptHandler(telemetryHelper).handle("inlineChat-editor", triggerId, requestData, sessionInfo, false)
            .catch {
                // TODO: log error and show in popup window
                e -> println("Error: $e")
            }
            .onEach { messages.add(it) }
            .toList()
        }
        chat.await()
        return messages
    }

    override fun dispose() {
        hidePopup()
    }
}

