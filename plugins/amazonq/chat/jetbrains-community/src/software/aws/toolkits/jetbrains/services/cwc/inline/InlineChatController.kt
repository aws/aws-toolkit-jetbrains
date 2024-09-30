// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cwc.inline

import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.jetbrains.rd.util.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import software.aws.toolkits.jetbrains.core.coroutines.disposableCoroutineScope
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
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContext
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.FileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.util.LanguageExtractor
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessage
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.storage.ChatSessionStorage
import software.aws.toolkits.telemetry.FeatureId
import java.util.Stack
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean


class InlineChatController(
    private val editor: Editor,
    private val project: Project
) : Disposable {
    private var currentPopup: JBPopup? = null
    private val scope = disposableCoroutineScope(this)
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

    private val popupSubmitHandler: suspend (String, String, Int) -> String = { prompt: String, selectedCode: String, selectedLineStart: Int ->
//        val selectedCode = getSelectedText(editor)
        runBlocking {
            isInProgress.set(true)
            val message = handleChat(prompt, selectedCode, editor, selectedLineStart)
            message
        }
    }

    private val popupCancelHandler: () -> Unit = {
        if (isPopupAborted.get() && currentPopup != null) {
            scope.launch(Dispatchers.EDT) {
                while (partialUndoActions.isNotEmpty()) {
                    val action = partialUndoActions.pop()
                    runChangeAction(project, action)
                }
                partialAcceptActions.clear()
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                recordInlineChatTelemetry(InlineChatUserDecision.DISMISS)
            }
        }
    }

    private fun recordInlineChatTelemetry(decision: InlineChatUserDecision) {
        if(metrics == null) return
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


    val diffAcceptHandler: () -> Unit = {
        scope.launch(Dispatchers.EDT) {
            partialUndoActions.clear()
                while (partialAcceptActions.isNotEmpty()) {
                    val action = partialAcceptActions.pop()
                    runChangeAction(project, action)
                }
            invokeLater { hidePopup() }
//            hidePopup()
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            recordInlineChatTelemetry(InlineChatUserDecision.ACCEPT)
        }

    }

     val diffRejectHandler: () -> Unit = {
            scope.launch(Dispatchers.EDT) {
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
            //                            telemetryHelper.recordInlineChatTelemetry(prompt.length, numOfLinesSelected, true,
//                                InlineChatUserDecision.DISMISS, 0.0, requestEndLatency)
        }
        popup.addListener(popupListener)
    }


    fun initPopup () {
//        currentPopup?.dispose()
        currentPopup?.let { Disposer.dispose(it) }
        currentPopup = InlineChatPopupFactory(acceptHandler = diffAcceptHandler, rejectHandler = diffRejectHandler, editor = editor,
            telemetryHelper = telemetryHelper, submitHandler = popupSubmitHandler, cancelHandler = popupCancelHandler, scope = scope).createPopup()
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
        rangeHighlighter= editor.markupModel.addRangeHighlighter(
            startOffset, endOffset, HighlighterLayer.SELECTION + 1,
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


    private fun hidePopup() {
        isPopupAborted.set(false)
        currentPopup?.closeOk(null)
        currentPopup = null
        isInProgress.set(false)
        shouldShowActions.set(false)
    }

    private fun getCodeBlocks(src: String): List<String> {
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

    private fun compareDiffs(original: List<String>, recommendation: List<String>): List<DiffRow> {
        val generator = DiffRowGenerator.create().showInlineDiffs(false).build()
        val rows: List<DiffRow> = generator.generateDiffRows(original, recommendation)
        return rows
    }

//    private fun incrementRowNumber() {
//        selectionStartLine.incrementAndGet()
//    }
//
//    private fun decrementRowNumber() {
//        selectionStartLine.decrementAndGet()
//    }
//
//    private fun getRowNumber(index: Int): Int {
//        return index + selectionStartLine.get()
//    }

    fun getShouldShowActions(): Boolean {
        return shouldShowActions.get()
    }

    fun getIsInProgress(): Boolean {
        return isInProgress.get()
    }

    private fun unescape(s: String): String {
        return StringEscapeUtils.unescapeHtml3(s)
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("=&gt;", "=>")
    }

    private suspend fun processChatMessage(selectedCode: String, event: ChatMessage, editor: Editor, selectedLineStart: Int) {
        if(event.message?.isNotEmpty() == true) {
            val codeBlocks = getCodeBlocks(event.message)
            if(codeBlocks.isEmpty()) {
                if (event.messageType == ChatMessageType.Answer) {
                    isInProgress.set(false)
//                    logger.warn { "No code block found in inline chat response with requestId: ${event.messageId}" }
                }
                logger.info { "No code block found in inline chat response with requestId: ${event.messageId} \nresponse: ${event.message} " }
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
            if(event.codeReference?.isNotEmpty() == true) {
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
            diff.forEach { row ->
                when (row.tag) {
                    DiffRow.Tag.EQUAL -> {
                        currentDocumentLine++
                        insertLine++
                    }
                    DiffRow.Tag.DELETE, DiffRow.Tag.CHANGE -> {
                        try {
                            if (row.tag == DiffRow.Tag.CHANGE && row.newLine.trimIndent() == row.oldLine?.trimIndent()) return
                            showCodeChangeInEditor(row, currentDocumentLine, editor)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
//                        currentDocumentLine++

                        if (row.tag == DiffRow.Tag.CHANGE) {
                            insertLine+=2
                            currentDocumentLine+=2
                        } else {
                            insertLine++
                            currentDocumentLine++
                        }
                        deletedLinesCount++
                        deletedCharsCount += row.oldLine?.length?: 0
                    }
                    DiffRow.Tag.INSERT -> {
                        try {
                            showCodeChangeInEditor(row, insertLine, editor)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        insertLine++
                        addedLinesCount++
                        addedCharsCount += row.newLine?.length?: 0
                    }
                }
            }
            isInProgress.set(false)
            shouldShowActions.set(true)
            metrics?.numSuggestionAddChars = addedCharsCount
            metrics?.numSuggestionAddLines = addedLinesCount
            metrics?.numSuggestionDelChars = deletedCharsCount
            metrics?.numSuggestionDelLines = deletedLinesCount
        }
    }


    private fun insertNewLineIfNeeded(row: Int, document: Document) : Int {
        var newLineInserted = 0
        while (row > document.lineCount - 1) {
            document.insertString(document.textLength, "\n")
            newLineInserted++
        }
        return newLineInserted
    }

    private fun getLineStartOffset(document: Document, row: Int): Int {
        return ReadAction.compute<Int, Throwable> {
            document.getLineStartOffset(row)
        }
    }

    private fun getLineEndOffset(document: Document, row: Int): Int {
        return ReadAction.compute<Int, Throwable> {
            if (row == document.lineCount - 1) {
                document.getLineEndOffset(row)
            } else {
                document.getLineEndOffset(row) + 1
            }
        }
    }

    private fun getSelectionStartLine(editor: Editor): Int {
        return ReadAction.compute<Int, Throwable> {
            editor.document.getLineNumber(editor.selectionModel.selectionStart)
        }
    }

    private suspend fun runChangeAction(project: Project, action: () -> Unit, shouldRecordForUndo: Boolean = false) {
        withContext(EDT) {
            CommandProcessor.getInstance().executeCommand(project, {
                    ApplicationManager.getApplication().runWriteAction {
                        WriteCommandAction.runWriteCommandAction(project) {
                            action()
                        }
                    }

            }, "", null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION, shouldRecordForUndo)
        }
    }

    private suspend fun insertString(document: Document, offset: Int, text: String) : RangeMarker {
        var rangeMarker: RangeMarker? = null
        val action = {
            document.insertString(offset, text)
            rangeMarker = document.createRangeMarker(offset, offset + text.length)
            rangeMarker!!.isGreedyToLeft = true
            rangeMarker!!.isGreedyToRight = true
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

    private suspend fun highlightString(document: Document, start: Int, end: Int, isInsert: Boolean) : RangeMarker {
        var rangeMarker: RangeMarker? = null
        val action = {
            rangeMarker = document.createRangeMarker(start, end)
//            rangeMarker!!.isGreedyToLeft = true
//            rangeMarker!!.isGreedyToRight = true
            highlightCodeWithBackgroundColor(editor, rangeMarker!!.startOffset, rangeMarker!!.endOffset, isInsert)
        }
        runChangeAction(project, action)
        return rangeMarker!!
    }

    private suspend fun showCodeChangeInEditor(diffRow: DiffRow, row: Int, editor: Editor) {
        val document = editor.document
        when (diffRow.tag) {
            DiffRow.Tag.DELETE -> {
//                val rowNum = getRowNumber(index)
                val changeStartOffset = getLineStartOffset(document, row)
                val changeEndOffset = getLineEndOffset(document, row)
                val rangeMarker = highlightString(document, changeStartOffset, changeEndOffset, false)
                partialUndoActions.add {
                    editor.markupModel.removeAllHighlighters()
                }
                partialAcceptActions.add {
                    if (rangeMarker.isValid) {
                        scope.launch(Dispatchers.EDT) {
                            deleteString(document, rangeMarker.startOffset, rangeMarker.endOffset)
                        }
                    }
                    editor.markupModel.removeAllHighlighters()
//                    decrementRowNumber()
                }
            }

            DiffRow.Tag.INSERT -> {
//                    val insertRow = getRowNumber(index)
                val newLineInserted = insertNewLineIfNeeded(row, document)
                val insertOffset = getLineStartOffset(document, row)
                val textToInsert =  unescape(diffRow.newLine) + "\n"
                val rangeMarker = insertString(document, insertOffset, textToInsert)
                partialUndoActions.add {
                    if (rangeMarker.isValid) {
                        scope.launch(Dispatchers.EDT) {
                            deleteString(document, rangeMarker.startOffset, rangeMarker.endOffset + newLineInserted)
                        }
                    }
                    editor.markupModel.removeAllHighlighters()
//                        decrementRowNumber()
                }
                partialAcceptActions.add {
                    editor.markupModel.removeAllHighlighters()
                }
//                    incrementRowNumber()
            }

            else -> {
                val changeOffset = getLineStartOffset(document, row)
                val changeEndOffset = getLineEndOffset(document, row)
                val oldTextRangeMarker = highlightString(document, changeOffset, changeEndOffset, false)
                partialAcceptActions.add {
                    scope.launch(Dispatchers.EDT) {
                            if (oldTextRangeMarker.isValid) {
                                deleteString(document, oldTextRangeMarker.startOffset, oldTextRangeMarker.endOffset)
                            }
                    }
                    editor.markupModel.removeAllHighlighters()
                }
                val insertOffset = getLineEndOffset(document, row)
                val newLineInserted = insertNewLineIfNeeded(row, document)
                val textToInsert = unescape(diffRow.newLine) + "\n"
                val newTextRangeMarker = insertString(document, insertOffset, textToInsert)
                partialUndoActions.add {
                    WriteCommandAction.runWriteCommandAction(project) {
                        if (newTextRangeMarker.isValid) {
                            scope.launch(Dispatchers.EDT) {
                                deleteString(document, newTextRangeMarker.startOffset, newTextRangeMarker.endOffset + newLineInserted)
                            }
                        }
                    }
                    editor.markupModel.removeAllHighlighters()
//                        decrementRowNumber()
                }
//                    incrementRowNumber()
            }
        }
    }


    private suspend fun handleChat (message: String, selectedCode: String = "", editor: Editor, selectedLineStart: Int) : String {
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

        val languageExtractor = LanguageExtractor()
        val intentRecognizer = UserIntentRecognizer()
        val language = editor.project?.let { languageExtractor.extractLanguageNameFromCurrentFile(editor, it) } ?: ""

        var baseRules = "- Plan out the changes step-by-step before making them, this should be brief and not include any code\n" +
            "- Do not explain the code after, the plan and code are sufficient\n"
        var prompt = ""
        if (selectedCode.isNotBlank()) {
            if (language.isNotEmpty()) {
                baseRules += "- Ensure the code is written in $language\n"
            }
            prompt = "Rules for writing code:\n" + baseRules +
                "Write a code snipped based on the following:\n" + message
        } else {
            baseRules += "- If the query is a question only attempt to add comments to the code that answer it\n" +
                "- Make sure to preserve the original indentation, code formatting, tab size and structure as much as possible\n" +
                "- Do not change the code more than required, try to maintain variables, function names, and other identifiers"
            prompt = "```$language\n$selectedCode```\n" +
                "Rules for rewriting the code:\n" + baseRules +
                "Rewrite the above code to do the following:\n" + message
        }

        logger.info { "Inline chat prompt: $prompt" }

        val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
        val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ChatMessage)
//        val fileContextExtractor = FileContextExtractor(null, project)
//        val fileContext = fileContextExtractor.extract()

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
            ChatPromptHandler(telemetryHelper).handle("inlineChat-editor", triggerId, requestData, sessionInfo, false, true)
//            sessionInfo.session.chat(requestData)
            .catch { e ->
                logger.warn { "Error in inline chat request: ${e.message}" }
                errorMessage = e.message ?: ""
            }
            .onEach { event: ChatMessage ->
                if (event.message?.isNotEmpty() == true && prevMessage != event.message) {
                    processChatMessage(selectedCode, event, editor, selectedLineStart)
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
        requestId?.let{
            metrics = InlineChatMetrics(requestId = it, inputLength = message.length,  numSelectedLines = selectedCode.split("\n").size,
                codeIntent = true, responseStartLatency = firstResponseLatency, responseEndLatency = lastResponseLatency)
        }

        return errorMessage
    }

    companion object {
        private val logger = getLogger<InlineChatController>()
    }

    override fun dispose() {
        currentPopup?.let { Disposer.dispose(it) }
        hidePopup()
    }
}


