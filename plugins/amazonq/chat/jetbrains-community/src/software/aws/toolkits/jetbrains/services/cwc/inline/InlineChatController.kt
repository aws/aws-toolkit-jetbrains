// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.jetbrains.rd.util.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import org.apache.commons.text.StringEscapeUtils
import software.amazon.awssdk.services.codewhispererruntime.model.InlineChatUserDecision
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.core.gettingstarted.requestCredentialsForQ
import software.aws.toolkits.jetbrains.core.webview.BrowserState
import software.aws.toolkits.jetbrains.services.amazonq.QWebviewPanel
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AMAZON_Q_WINDOW_ID
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.controller.ReferenceLogController
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.messenger.ChatPromptHandler
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.TelemetryHelper
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.userIntent.UserIntentRecognizer
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType
import software.aws.toolkits.jetbrains.services.cwc.inline.listeners.InlineChatFileListener
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionStorage
import software.aws.toolkits.telemetry.FeatureId
import java.util.Stack
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class InlineChatController(
    private val project: Project,
    private val scope: CoroutineScope
) : Disposable {
    private var currentPopup: JBPopup? = null
    private var rangeHighlighter: RangeHighlighter? = null
    private val partialUndoActions = Stack<() -> Unit>()
    private val partialAcceptActions = Stack<() -> Unit>()
    private var selectionStartLine = AtomicInteger(0)
    private val sessionStorage = ChatSessionStorage()
    private val telemetryHelper = TelemetryHelper(project, sessionStorage)
    private val shouldShowActions = AtomicBoolean(false)
    private val isInProgress = AtomicBoolean(false)
    private var metrics: InlineChatMetrics? = null
    private var isPopupAborted = AtomicBoolean(true)

    init {
        InlineChatFileListener(project).apply {
            project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        }
    }

    data class InlineChatMetrics(
        val requestId: String,
        val inputLength: Int? = null,
        val numSelectedLines: Int? = null,
        val codeIntent: Boolean? = null,
        var userDecision: InlineChatUserDecision? = null,
        val responseStartLatency: Double? = null,
        val responseEndLatency: Double? = null,
        var numSuggestionAddChars: Int? = null,
        var numSuggestionAddLines: Int? = null,
        var numSuggestionDelChars: Int? = null,
        var numSuggestionDelLines: Int? = null,
        var charactersAdded: Int? = null,
        var charactersRemoved: Int? = null,
    )

    private val popupSubmitHandler: suspend (String, String, Int, Editor) -> String = {
            prompt: String, selectedCode: String, selectedLineStart: Int, editor: Editor ->
        runBlocking {
            isInProgress.set(true)
            val message = handleChat(prompt, selectedCode, editor, selectedLineStart)
            message
        }
    }

    val popupCancelHandler: () -> Unit = {
        if (isPopupAborted.get() && currentPopup != null) {
            scope.launch(EDT) {
                while (partialUndoActions.isNotEmpty()) {
                    val action = partialUndoActions.pop()
                    runChangeAction(project, action)
                }
                partialAcceptActions.clear()
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                recordInlineChatTelemetry(InlineChatUserDecision.DISMISS)
            }
            currentPopup?.dispose()
        }
    }

    private fun recordInlineChatTelemetry(decision: InlineChatUserDecision) {
        if (metrics == null) return
        metrics?.userDecision = decision
        if (decision == InlineChatUserDecision.ACCEPT) {
            metrics?.charactersAdded = metrics?.numSuggestionAddChars
            metrics?.charactersRemoved = metrics?.numSuggestionDelChars
        }
        metrics?.requestId?.let {
            telemetryHelper.recordInlineChatTelemetry(
                it,
                metrics?.inputLength,
                metrics?.numSelectedLines,
                metrics?.codeIntent,
                metrics?.userDecision,
                metrics?.responseStartLatency,
                metrics?.responseEndLatency,
                metrics?.numSuggestionAddChars,
                metrics?.numSuggestionAddLines,
                metrics?.numSuggestionDelChars,
                metrics?.numSuggestionDelLines,
                metrics?.charactersAdded,
                metrics?.charactersRemoved
            )
        }
        metrics = null
    }

    private val diffAcceptHandler: () -> Unit = {
        scope.launch(EDT) {
            partialUndoActions.clear()
            while (partialAcceptActions.isNotEmpty()) {
                val action = partialAcceptActions.pop()
                runChangeAction(project, action)
            }
            invokeLater { hidePopup() }
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            recordInlineChatTelemetry(InlineChatUserDecision.ACCEPT)
        }
    }

    private val diffRejectHandler: () -> Unit = {
        scope.launch(EDT) {
            while (partialUndoActions.isNotEmpty()) {
                val action = partialUndoActions.pop()
                runChangeAction(project, action)
            }
            partialAcceptActions.clear()
            invokeLater { hidePopup() }
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            recordInlineChatTelemetry(InlineChatUserDecision.REJECT)
        }
    }

    private fun addPopupListeners(popup: JBPopup) {
        val popupListener = object : JBPopupListener {

            override fun onClosed(event: LightweightWindowEvent) {
                if (isPopupAborted.get() && event.asPopup().isDisposed) {
                    popupCancelHandler.invoke()
                }
            }
        }
        popup.addListener(popupListener)
    }

    fun initPopup(editor: Editor) {
        currentPopup?.let { Disposer.dispose(it) }
        currentPopup = InlineChatPopupFactory(
            acceptHandler = diffAcceptHandler, rejectHandler = diffRejectHandler, editor = editor,
            submitHandler = popupSubmitHandler, cancelHandler = popupCancelHandler
        ).createPopup(scope)
        addPopupListeners(currentPopup!!)
        Disposer.register(this, currentPopup!!)
        isPopupAborted.set(true)
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
        rangeHighlighter = editor.markupModel.addRangeHighlighter(
            startOffset, endOffset, HighlighterLayer.SELECTION + 1,
            attributes, HighlighterTargetArea.EXACT_RANGE
        )
    }

    private fun extractContentAfterFirstNewline(input: String): String {
        val newlineIndex = input.indexOf('\n')
        return if (newlineIndex != -1) {
            input.substring(newlineIndex + 1)
        } else {
            ""
        }
    }

    private fun hidePopup() {
        isPopupAborted.set(false)
        currentPopup?.closeOk(null)
        currentPopup = null
        isInProgress.set(false)
        shouldShowActions.set(false)
    }

    fun disposePopup() {
        currentPopup?.let { Disposer.dispose(it) }
        hidePopup()
    }

    private fun getCodeBlocks(src: String): List<String> {
        val codeBlocks = mutableListOf<String>()
        var currentIndex = 0

        while (currentIndex < src.length) {
            val startIndex = src.indexOf("```", currentIndex)
            if (startIndex == -1) return codeBlocks

            val endIndex = src.indexOf("```", startIndex + 3)
            if (endIndex == -1) return codeBlocks

            val code = src.substring(startIndex + 3, endIndex)
            codeBlocks.add(code)
            currentIndex = endIndex + 3
        }
        return codeBlocks
    }

    private fun compareDiffs(original: List<String>, recommendation: List<String>): List<DiffRow> {
        val generator = DiffRowGenerator.create().showInlineDiffs(false).build()
        val rows: List<DiffRow> = generator.generateDiffRows(original, recommendation)
        return rows
    }

    private fun unescape(s: String): String = StringEscapeUtils.unescapeHtml3(s)
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("=&gt;", "=>")

    private suspend fun processChatMessage(selectedCode: String, event: ChatMessage, editor: Editor, selectedLineStart: Int, prevMessage: String) {
        if (event.message?.isNotEmpty() == true) {
            val codeBlocks = getCodeBlocks(event.message)
            if (codeBlocks.isEmpty()) {
                logger.info { "No code block found in inline chat response with requestId: ${event.messageId}" }
                return
            }
            val recommendation = unescape(extractContentAfterFirstNewline(codeBlocks.first()))
            logger.info { "Recived Inline chat code recommendation:\n ```$recommendation``` \nfrom requestId: ${event.messageId}" }
            logger.info { "Original selected code:\n ```$selectedCode```" }
            val diff = compareDiffs(selectedCode.split("\n"), recommendation.split("\n"))
            while (partialUndoActions.isNotEmpty()) {
                val action = partialUndoActions.pop()
                action.invoke()
            }
            partialAcceptActions.clear()
            selectionStartLine = AtomicInteger(selectedLineStart)
            var currentDocumentLine = selectedLineStart
            var insertLine = selectedLineStart
            if (event.codeReference?.isNotEmpty() == true) {
                editor.project?.let { ReferenceLogController.addReferenceLog(recommendation, event.codeReference, editor, it) }
            }

            var deletedCharsCount = 0
            var addedCharsCount = 0
            var addedLinesCount = 0
            var deletedLinesCount = 0

            if (currentPopup?.isVisible != true) {
                logger.debug { "inline chat popup cancelled before diff is shown" }
                isInProgress.set(false)
                recordInlineChatTelemetry(InlineChatUserDecision.DISMISS)
                return
            }
            var isAllEqual = true
            diff.forEach { row ->
                when (row.tag) {
                    DiffRow.Tag.EQUAL -> {
                        currentDocumentLine++
                        insertLine++
                    }
                    DiffRow.Tag.DELETE, DiffRow.Tag.CHANGE -> {
                        if (row.tag == DiffRow.Tag.CHANGE && row.newLine.trimIndent() == row.oldLine?.trimIndent()) return
                        isAllEqual = false
                        showCodeChangeInEditor(row, currentDocumentLine, editor)

                        if (row.tag == DiffRow.Tag.CHANGE) {
                            insertLine += 2
                            currentDocumentLine += 2
                        } else {
                            insertLine++
                            currentDocumentLine++
                        }
                        deletedLinesCount++
                        deletedCharsCount += row.oldLine?.length ?: 0
                    }
                    DiffRow.Tag.INSERT -> {
                        isAllEqual = false
                        showCodeChangeInEditor(row, insertLine, editor)

                        insertLine++
                        addedLinesCount++
                        addedCharsCount += row.newLine?.length ?: 0
                    }
                }
            }
            if (isAllEqual) {
                throw Exception("No recommendation provided. Please try again with a different question.")
            }

            isInProgress.set(false)
            shouldShowActions.set(true)
            metrics?.numSuggestionAddChars = addedCharsCount
            metrics?.numSuggestionAddLines = addedLinesCount
            metrics?.numSuggestionDelChars = deletedCharsCount
            metrics?.numSuggestionDelLines = deletedLinesCount
        } else {
            if (event.messageType == ChatMessageType.Answer) {
                val codeBlocks = getCodeBlocks(prevMessage)
                if (codeBlocks.isEmpty()) {
                    logger.warn { "No code block found in inline chat response with requestId: ${event.messageId} \nresponse: ${event.message}" }
                    isInProgress.set(false)
                    throw Exception("No recommendation provided. Please try again with a different question.")
                }
            }
        }
    }

    private suspend fun insertNewLineIfNeeded(row: Int, editor: Editor): Int {
        var newLineInserted = 0
        while (row > editor.document.lineCount - 1) {
            insertString(editor, editor.document.textLength, "\n")
            newLineInserted++
        }
        return newLineInserted
    }

    private fun getLineStartOffset(document: Document, row: Int): Int = ReadAction.compute<Int, Throwable> {
        document.getLineStartOffset(row)
    }

    private fun getLineEndOffset(document: Document, row: Int): Int = ReadAction.compute<Int, Throwable> {
        if (row == document.lineCount - 1) {
            document.getLineEndOffset(row)
        } else {
            document.getLineEndOffset(row) + 1
        }
    }

    private suspend fun runChangeAction(project: Project, action: () -> Unit) {
        withContext(EDT) {
            CommandProcessor.getInstance().executeCommand(project, {
                ApplicationManager.getApplication().runWriteAction {
                    WriteCommandAction.runWriteCommandAction(project) {
                        action()
                    }
                }
            }, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, false)
        }
    }

    private suspend fun insertString(editor: Editor, offset: Int, text: String): RangeMarker {
        var rangeMarker: RangeMarker? = null
        val action = {
            editor.document.insertString(offset, text)
//            CodeStyleManager.getInstance(project).adjustLineIndent(document, offset)
            val row = editor.document.getLineNumber(offset)
            rangeMarker = editor.document.createRangeMarker(offset, getLineEndOffset(editor.document, row))
            highlightCodeWithBackgroundColor(editor, rangeMarker!!.startOffset, rangeMarker!!.endOffset, true)
        }
        runChangeAction(project, action)
        return rangeMarker!!
    }

    private suspend fun deleteString(document: Document, start: Int, end: Int) {
        val action = {
            document.deleteString(start, end)
        }
        runChangeAction(project, action)
    }

    private suspend fun highlightString(editor: Editor, start: Int, end: Int, isInsert: Boolean): RangeMarker {
        var rangeMarker: RangeMarker? = null
        val action = {
            rangeMarker = editor.document.createRangeMarker(start, end)
            highlightCodeWithBackgroundColor(editor, rangeMarker!!.startOffset, rangeMarker!!.endOffset, isInsert)
        }
        runChangeAction(project, action)
        return rangeMarker!!
    }

    private suspend fun showCodeChangeInEditor(diffRow: DiffRow, row: Int, editor: Editor) {
        try {
            val document = editor.document
            when (diffRow.tag) {
                DiffRow.Tag.DELETE -> {
                    val changeStartOffset = getLineStartOffset(document, row)
                    val changeEndOffset = getLineEndOffset(document, row)
                    val rangeMarker = highlightString(editor, changeStartOffset, changeEndOffset, false)
                    partialUndoActions.add {
                        editor.markupModel.removeAllHighlighters()
                    }
                    partialAcceptActions.add {
                        if (rangeMarker.isValid) {
                            scope.launch(EDT) {
                                deleteString(document, rangeMarker.startOffset, rangeMarker.endOffset)
                            }
                        }
                        editor.markupModel.removeAllHighlighters()
                    }
                }

                DiffRow.Tag.INSERT -> {
                    val newLineInserted = insertNewLineIfNeeded(row, editor)
                    val insertOffset = getLineStartOffset(document, row)
                    val textToInsert = unescape(diffRow.newLine) + "\n"
                    val rangeMarker = insertString(editor, insertOffset, textToInsert)
                    partialUndoActions.add {
                        if (rangeMarker.isValid) {
                            scope.launch(EDT) {
                                deleteString(document, rangeMarker.startOffset, rangeMarker.endOffset + newLineInserted)
                            }
                        }
                        editor.markupModel.removeAllHighlighters()
                    }
                    partialAcceptActions.add {
                        editor.markupModel.removeAllHighlighters()
                    }
                }

                else -> {
                    val changeOffset = getLineStartOffset(document, row)
                    val changeEndOffset = getLineEndOffset(document, row)
                    val oldTextRangeMarker = highlightString(editor, changeOffset, changeEndOffset, false)
                    partialAcceptActions.add {
                        scope.launch(EDT) {
                            if (oldTextRangeMarker.isValid) {
                                deleteString(document, oldTextRangeMarker.startOffset, oldTextRangeMarker.endOffset)
                            }
                        }
                        editor.markupModel.removeAllHighlighters()
                    }
                    val insertOffset = getLineEndOffset(document, row)
                    val newLineInserted = insertNewLineIfNeeded(row, editor)
                    val textToInsert = unescape(diffRow.newLine) + "\n"
                    val newTextRangeMarker = insertString(editor, insertOffset, textToInsert)
                    partialUndoActions.add {
                        WriteCommandAction.runWriteCommandAction(project) {
                            if (newTextRangeMarker.isValid) {
                                scope.launch(EDT) {
                                    deleteString(document, newTextRangeMarker.startOffset, newTextRangeMarker.endOffset + newLineInserted)
                                }
                            }
                        }
                        editor.markupModel.removeAllHighlighters()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn { "Error when showing inline chat diff in editor: ${e.message} \n ${e.stackTraceToString()}" }
            throw Exception("Unexpected error, please try again.")
        }
    }

    private suspend fun handleChat(message: String, selectedCode: String = "", editor: Editor, selectedLineStart: Int): String {
        val authController = AuthController()
        val credentialState = authController.getAuthNeededStates(project).chat
        if (credentialState != null) {
            // handle auth
            if (!JBCefApp.isSupported()) {
                requestCredentialsForQ(project)
            } else {
                runInEdt {
                    QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.Q))
                    ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)?.activate(null, false)
                    ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)?.show()
                }
            }
            return "Please sign in to Amazon Q"
        }
        val startTime = System.currentTimeMillis()
        var firstResponseLatency = 0.0
        val messages = mutableListOf<ChatMessage>()
        val triggerId = UUID.randomUUID().toString()
        val intentRecognizer = UserIntentRecognizer()

//        val languageExtractor = LanguageExtractor()
//        val language = editor.project?.let { languageExtractor.extractProgrammingLanguageNameFromCurrentFile(editor, it) } ?: ""
//        This is temporary. TODO: remove this after prompt added on service side
        var prompt = ""
        if (selectedCode.isNotBlank()) {
            prompt = "You are a code transformation assistant." +
                " Your task is to modify a selection of lines from a given code file according to a specific instruction.\n" +
                "Follow these steps carefully:\n" +
                "- You will be given some selected code from a file to be transformed, enclosed in <selected_code></selected_code> XML tags\n" +
                "- You will receive an instruction for how to transform the selected code, enclosed in <instruction></instruction> XML tags\n" +
                "- You will be given the contents of that same file as context, enclosed in <context></context> XML tags\n"
            "- Your task is to:\n" +
                "- Apply the transformation instruction to the selected code\n" +
                "- Ensure that the transformation is applied correctly and consistently\n" +
                "- Reuse existing functions and other code from the context wherever possible\n" +
                "- Important rules to follow:\n" +
                "- Maintain the original indentation of the selected code\n" +
                "- If the instruction asks to provide explanations or answer questions about the code," +
                " add these as new comment lines above the relevant lines in the code; do not change the code lines themselves\n" +
                "- If the instruction is unclear or cannot be applied, do not make any changes to the code\n" +
                "- After performing the transformation, return the transformed code." +
                " If the transformation generates new code but does not modify the selected code, be sure to include the selected code in your response.\n"
        } else {
            prompt = "You are a coding assistant. Your task is to generate code according to an specific instruction.\n" +
                "Follow these steps carefully:\n" +
                "- You will receive an instruction for how to generate code, enclosed in <instruction></instruction> XML tags\n" +
                "- You will be given the contents of that same file as context, enclosed in <context></context> XML tags\n" +
                "- Your task is to:\n" +
                "- Generate code according to the instruction\n" +
                "- Reuse existing functions and other code from the context wherever possible\n" +
                "- Important rules to follow:\n" +
                "- If the instruction asks to provide explanations or answer questions about the code," +
                " add these as new comment lines above the relevant lines in the code; do not change the code lines themselves\n" +
                "- If the instruction is unclear or cannot be applied, do not make any changes to the code\n" +
                "- After generating the code, return ONLY the new code you generated; do not include any existing lines of code from the context.\n"
        }

        prompt += "- Respond with the code in markdown format. Do not include any explanations or other text outside of the code itself\n" +
            "Remember, your output should contain nothing but the transformed code or code comments.\n"
        if (selectedCode.isNotBlank()) { prompt += "<selected_code>$selectedCode</selected_code>\n" }
        prompt += "<instruction>$message</instruction>\n"
        prompt += "<context>${editor.document.text.take(8000)}</context>"

        logger.info { "Inline chat prompt: $prompt" }

        val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
        val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ChatMessage)

        val requestData = ChatRequestData(
            tabId = "inlineChat-editor",
            message = prompt,
            activeFileContext = fileContext,
            userIntent = intentRecognizer.getUserIntentFromPromptChatMessage(message),
            triggerType = TriggerType.Click,
            customization = CodeWhispererModelConfigurator.getInstance().activeCustomization(project),
            relevantTextDocuments = emptyList()
        )

        val sessionInfo = sessionStorage.getSession("inlineChat-editor", project)

        // Save the request in the history
        sessionInfo.history.add(requestData)
        var errorMessage = ""
        var prevMessage = ""
        val chat = sessionInfo.scope.async {
            ChatPromptHandler(telemetryHelper).handle(
                "inlineChat-editor",
                triggerId,
                requestData,
                sessionInfo,
                false,
                true
            )
                .catch { e ->
                    logger.warn { "Error in inline chat request: ${e.message}" }
                    errorMessage = e.message ?: ""
                }
                .onEach { event: ChatMessage ->
                    if (event.message?.isNotEmpty() == true && prevMessage != event.message) {
                        runBlocking { processChatMessage(selectedCode, event, editor, selectedLineStart, prevMessage) }
                        prevMessage = event.message
                    }
                    if (messages.isEmpty()) {
                        firstResponseLatency = (System.currentTimeMillis() - startTime).toDouble()
                    }
                    messages.add(event)
                }
                .toList()
        }
        chat.await()
        val lastResponseLatency = (System.currentTimeMillis() - startTime).toDouble()
        val requestId = messages.lastOrNull()?.messageId
        requestId?.let {
            metrics = InlineChatMetrics(
                requestId = it, inputLength = message.length, numSelectedLines = selectedCode.split("\n").size,
                codeIntent = true, responseStartLatency = firstResponseLatency, responseEndLatency = lastResponseLatency
            )
        }

        return errorMessage
    }

    companion object {
        fun getInstance(project: Project) = project.service<InlineChatController>()
        private val logger = getLogger<InlineChatController>()
    }

    override fun dispose() {
        currentPopup?.let { Disposer.dispose(it) }
        hidePopup()
    }
}
