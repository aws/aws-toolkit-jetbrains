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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
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
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
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
    private val scope: CoroutineScope,
) : Disposable {
    private var currentPopup: JBPopup? = null
    private var rangeHighlighter: RangeHighlighter? = null
    private val partialUndoActions = Stack<() -> Unit>()
    private val partialAcceptActions = Stack<() -> Unit>()
    private var insertionLine = AtomicInteger(-1)
    private val sessionStorage = ChatSessionStorage()
    private val telemetryHelper = TelemetryHelper(project, sessionStorage)
    private val shouldShowActions = AtomicBoolean(false)
    private val isInProgress = AtomicBoolean(false)
    private var metrics: InlineChatMetrics? = null
    private var canPopupAbort = AtomicBoolean(true)
    private var currentSelectionRange: RangeMarker? = null

    init {
        InlineChatFileListener(project, this).apply {
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

    val popupCancelHandler: (editor: Editor) -> Unit = { editor ->
        if (canPopupAbort.get() && currentPopup != null) {
            undoChanges()
            restoreSelection(editor)
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

    private fun undoChanges () {
        scope.launch(EDT) {
            while (partialUndoActions.isNotEmpty()) {
                val action = partialUndoActions.pop()
                action.invoke()
            }
            partialAcceptActions.clear()
        }
    }

    private val diffAcceptHandler: () -> Unit = {
        scope.launch(EDT) {
            partialUndoActions.clear()
            while (partialAcceptActions.isNotEmpty()) {
                val action = partialAcceptActions.pop()
                action.invoke()
            }
            invokeLater { hidePopup() }
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            recordInlineChatTelemetry(InlineChatUserDecision.ACCEPT)
        }
    }

    private val diffRejectHandler: (editor: Editor) -> Unit = { editor ->
        undoChanges()
        invokeLater { hidePopup() }
        restoreSelection(editor)
        ApplicationManager.getApplication().executeOnPooledThread {
            recordInlineChatTelemetry(InlineChatUserDecision.REJECT)
        }
    }

    private fun addPopupListeners(popup: JBPopup, editor: Editor) {
        val popupListener = object : JBPopupListener {

            override fun onClosed(event: LightweightWindowEvent) {
                if (canPopupAbort.get() && event.asPopup().isDisposed) {
                    popupCancelHandler.invoke(editor)
                }
            }
        }
        popup.addListener(popupListener)
    }

    fun initPopup(editor: Editor) {
        currentPopup?.let { Disposer.dispose(it) }
        currentPopup = InlineChatPopupFactory(
            acceptHandler = diffAcceptHandler, rejectHandler = { diffRejectHandler(editor) },
            submitHandler = popupSubmitHandler, cancelHandler = { popupCancelHandler(editor) }
        ).createPopup(editor, scope)
        addPopupListeners(currentPopup!!, editor)
        Disposer.register(this, currentPopup!!)
        canPopupAbort.set(true)
    }

    private fun removeSelection(editor: Editor) {
        scope.launch(EDT) {
            val selectionModel = editor.selectionModel
            selectionModel.removeSelection()
        }
    }

    private fun restoreSelection(editor: Editor) {
        currentSelectionRange?.let { range ->
            scope.launch(EDT) {
                val selectionModel = editor.selectionModel
                selectionModel.setSelection(range.startOffset, range.endOffset)
            }
        }
        currentSelectionRange = null
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

    private fun hidePopup() {
        canPopupAbort.set(false)
        currentPopup?.closeOk(null)
        isInProgress.set(false)
        shouldShowActions.set(false)
        currentSelectionRange = null
    }

    fun disposePopup(isFromFileChange: Boolean) {
        if (currentPopup != null && !shouldShowActions.get() || isFromFileChange) {
            currentPopup?.let { Disposer.dispose(it) }
            hidePopup()
            currentPopup = null
        }
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

    private fun processNewCode(editor: Editor, line: Int, code: String, prevMessage: String) {
        logger.info("received inline chat recommendation with code: \n $code")
        var insertLine = line
        var linesToAdd = emptyList<String>()
        val prevLines = prevMessage.split("\n")
        if (prevLines.size > 1 && code.startsWith(prevMessage)) {
            if (insertionLine.get() != -1) insertLine = insertionLine.get()
            linesToAdd = code.split("\n").drop(prevLines.size - 1)
        } else {
            while (partialUndoActions.isNotEmpty()) {
                val action = partialUndoActions.pop()
                action.invoke()
            }
            partialAcceptActions.clear()
            linesToAdd = code.split("\n")
        }
        if (linesToAdd.last() == "") linesToAdd = linesToAdd.dropLast(1)
        linesToAdd.forEach { l ->
            val row = DiffRow(DiffRow.Tag.INSERT, "", l)
            showCodeChangeInEditor(row, insertLine, editor)
            insertLine++
            insertionLine.set(insertLine)
        }
    }

    private fun processDiffRows(diffRows: List<DiffRow>, selectionRange: RangeMarker, editor: Editor): Boolean {
        var isAllEqual = true
        val startLine = getLineNumber(editor.document, selectionRange.startOffset)
        var currentDocumentLine = startLine

        var deletedCharsCount = 0
        var addedCharsCount = 0
        var addedLinesCount = 0
        var deletedLinesCount = 0
        removeSelection(editor)
        diffRows.forEach { row ->
            when (row.tag) {
                DiffRow.Tag.EQUAL -> {
                    currentDocumentLine++
                }

                DiffRow.Tag.DELETE -> {
                    isAllEqual = false
                    showCodeChangeInEditor(row, currentDocumentLine, editor)
                    currentDocumentLine++
                    deletedLinesCount++
                    deletedCharsCount += row.oldLine?.length ?: 0
                }

                DiffRow.Tag.CHANGE -> {
                    if (row.newLine.trimIndent() != row.oldLine?.trimIndent()) {
                        isAllEqual = false
                        showCodeChangeInEditor(row, currentDocumentLine, editor)
                        currentDocumentLine += 2
                        deletedLinesCount++
                        deletedCharsCount += row.oldLine?.length ?: 0
                        addedLinesCount++
                        addedCharsCount += row.newLine?.length ?: 0
                    } else {
                        currentDocumentLine++
                    }
                }

                DiffRow.Tag.INSERT -> {
                    isAllEqual = false
                    showCodeChangeInEditor(row, currentDocumentLine, editor)
                    currentDocumentLine++
                    addedLinesCount++
                    addedCharsCount += row.newLine?.length ?: 0
                }
            }
        }
        metrics?.numSuggestionAddChars = addedCharsCount
        metrics?.numSuggestionAddLines = addedLinesCount
        metrics?.numSuggestionDelChars = deletedCharsCount
        metrics?.numSuggestionDelLines = deletedLinesCount
        return isAllEqual
    }

    private fun processChatDiff(selectedCode: String, event: ChatMessage, editor: Editor, selectionRange: RangeMarker) {
        if (event.message?.isNotEmpty() == true) {
            runBlocking {
                while (partialUndoActions.isNotEmpty()) {
                    val action = partialUndoActions.pop()
                    runBlocking { action.invoke() }
                }
                partialAcceptActions.clear()

                val recommendation = unescape(event.message)
                logger.info { "Received Inline chat code recommendation:\n ```$recommendation``` \nfrom requestId: ${event.messageId}" }
                logger.info { "Original selected code:\n ```$selectedCode```" }
                if (selectedCode == recommendation) {
                    throw Exception("No suggestions from Q; please try a different instruction.")
                }
                val selection = selectedCode.split("\n")
                val recommendationList = recommendation.split("\n")
                val diff = compareDiffs(selection, recommendationList)

                if (currentPopup?.isVisible != true) {
                    logger.debug { "inline chat popup cancelled before diff is shown" }
                    isInProgress.set(false)
                    recordInlineChatTelemetry(InlineChatUserDecision.DISMISS)
                    return@runBlocking
                }

                val isAllEqual = processDiffRows(diff, selectionRange, editor)
                if (isAllEqual) {
                    throw Exception("No suggestions from Q; please try a different instruction.")
                }

                isInProgress.set(false)
                shouldShowActions.set(true)
            }
        }
    }

    private fun insertNewLineIfNeeded(row: Int, editor: Editor): Int {
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

    private fun getLineNumber(document: Document, offset: Int): Int = ReadAction.compute<Int, Throwable> {
        document.getLineNumber(offset)
    }

    private fun insertString(editor: Editor, offset: Int, text: String): RangeMarker {
        var rangeMarker: RangeMarker? = null

        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.insertString(offset, text)
                    val row = editor.document.getLineNumber(offset)
                    rangeMarker = editor.document.createRangeMarker(offset, getLineEndOffset(editor.document, row))
                }
                rangeMarker?.let { marker ->
                    highlightCodeWithBackgroundColor(editor, marker.startOffset, marker.endOffset, true)
                }
            }
        }

        return rangeMarker!!
    }

    private fun deleteString(document: Document, start: Int, end: Int) {
        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    document.deleteString(start, end)
                }
            }
        }
    }

    private fun highlightString(editor: Editor, start: Int, end: Int, isInsert: Boolean): RangeMarker {
        var rangeMarker: RangeMarker? = null
        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    rangeMarker = editor.document.createRangeMarker(start, end)
                    highlightCodeWithBackgroundColor(editor, rangeMarker!!.startOffset, rangeMarker!!.endOffset, isInsert)
                }
            }
        }
        return rangeMarker!!
    }

    private fun showCodeChangeInEditor(diffRow: DiffRow, row: Int, editor: Editor) {
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
                            deleteString(document, rangeMarker.startOffset, rangeMarker.endOffset)
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
                            deleteString(document, rangeMarker.startOffset, (rangeMarker.endOffset + newLineInserted).coerceAtMost(document.textLength))
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
                        if (oldTextRangeMarker.isValid) {
                            deleteString(document, oldTextRangeMarker.startOffset, oldTextRangeMarker.endOffset)
                        }
                        editor.markupModel.removeAllHighlighters()
                    }
                    val insertOffset = getLineEndOffset(document, row)
                    val newLineInserted = insertNewLineIfNeeded(row, editor)
                    val textToInsert = unescape(diffRow.newLine) + "\n"
                    val newTextRangeMarker = insertString(editor, insertOffset, textToInsert)
                    partialUndoActions.add {
                        if (newTextRangeMarker.isValid) {
                            deleteString(document, newTextRangeMarker.startOffset, newTextRangeMarker.endOffset + newLineInserted)
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

    private fun checkCredentials(): String? {
        val authController = AuthController()
        val credentialState = authController.getAuthNeededStates(project).chat
        if (credentialState != null) {
            if (!JBCefApp.isSupported()) {
                requestCredentialsForQ(project, isReauth = false)
            } else {
                runInEdt {
                    QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.Q))
                    ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)?.activate(null, false)
                    ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)?.show()
                }
            }
            scope.launch {
                delay(3000)
                withContext(EDT) {
                    disposePopup(true)
                }
            }
            return "Please sign in to Amazon Q"
        }
        return null
    }

    private suspend fun handleChat(message: String, selectedCode: String = "", editor: Editor, selectedLineStart: Int): String {
        val authError = checkCredentials()
        if (authError != null) {
            return authError
        }
        val selectionStart = getLineStartOffset(editor.document, selectedLineStart)
        var selectionRange: RangeMarker? = null
        if (selectedCode.isNotEmpty()) {
            WriteCommandAction.runWriteCommandAction(project) {
                selectionRange = editor.document.createRangeMarker(selectionStart, selectionStart + selectedCode.length)
                currentSelectionRange = selectionRange
            }
        }
        val startTime = System.currentTimeMillis()
        var firstResponseLatency = 0.0
        val messages = mutableListOf<ChatMessage>()
        val triggerId = UUID.randomUUID().toString()
        val intentRecognizer = UserIntentRecognizer()

        val language = editor.virtualFile?.programmingLanguage()
        var prompt = ""
        if (selectedCode.isNotBlank()) { prompt += "<selected_code>$selectedCode</selected_code>\n" }
        prompt += "<instruction>$message</instruction>\n"
        prompt += "<context>${if (editor.document.text.isNotEmpty()) editor.document.text.take(8000) else "file written in $language"}</context>"

        logger.info { "Inline chat prompt: $prompt" }

        val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
        val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ChatMessage)

        val requestData = ChatRequestData(
            tabId = "inlineChat-editor",
            message = prompt,
            activeFileContext = fileContext,
            userIntent = intentRecognizer.getUserIntentFromPromptChatMessage(message, null),
            triggerType = TriggerType.Inline,
            customization = CodeWhispererModelConfigurator.getInstance().activeCustomization(project),
            relevantTextDocuments = emptyList(),
            useRelevantDocuments = false
        )

        val sessionInfo = sessionStorage.getSession("inlineChat-editor", project)

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
                    errorMessage = "Error processing request; please try again"
                }
                .onEach { event: ChatMessage ->
                    if (event.message?.isNotEmpty() == true && prevMessage != event.message) {
                        try {
                            processNewCode(editor, selectedLineStart, unescape(event.message), prevMessage)
                        } catch (e: Exception) {
                            logger.info("error streaming chat message to editor: ${e.stackTraceToString()}")
                            errorMessage = e.message ?: "Error processing request; please try again."
                        }
                        prevMessage = unescape(event.message)
                    }
                    if (messages.isEmpty()) {
                        firstResponseLatency = (System.currentTimeMillis() - startTime).toDouble()
                    }
                    messages.add(event)
                }
                .toList()
        }
        chat.await()
        val finalMessage = messages.lastOrNull { m -> m.messageType == ChatMessageType.AnswerPart }
        if (selectionRange != null && finalMessage != null) {
            try {
                processChatDiff(selectedCode, finalMessage, editor, selectionRange!!)
            } catch (e: Exception) {
                logger.info("error precessing chat diff in editor: ${e.stackTraceToString()}")
                errorMessage = "Error processing request; please try again."
            }
        }
        insertionLine.set(-1)
        val lastResponseLatency = (System.currentTimeMillis() - startTime).toDouble()
        val requestId = messages.lastOrNull()?.messageId
        requestId?.let {
            metrics = InlineChatMetrics(
                requestId = it, inputLength = message.length, numSelectedLines = selectedCode.split("\n").size,
                codeIntent = true, responseStartLatency = firstResponseLatency, responseEndLatency = lastResponseLatency
            )
        }
        if(errorMessage.isNotEmpty()) {
            undoChanges()
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
