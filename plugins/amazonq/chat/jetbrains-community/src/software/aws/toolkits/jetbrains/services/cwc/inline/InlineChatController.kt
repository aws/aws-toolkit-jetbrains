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
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.QFeatureEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.broadcastQEvent
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
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.FeatureId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@Service(Service.Level.PROJECT)
class InlineChatController(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {
    private var currentPopup: JBPopup? = null
    private var rangeHighlighter: RangeHighlighter? = null
    private var rejectAction: (() -> Unit)? = null
    private var acceptAction: (() -> Unit)? = null
    private var insertionLine = AtomicInteger(-1)
    private val sessionStorage = ChatSessionStorage()
    private val telemetryHelper = TelemetryHelper(project, sessionStorage)
    private val shouldShowActions = AtomicBoolean(false)
    private val isInProgress = AtomicBoolean(false)
    private var metrics: InlineChatMetrics? = null
    private var canPopupAbort = AtomicBoolean(true)
    private var currentSelectionRange: RangeMarker? = null
    private val listener = InlineChatFileListener(project, this)
    private var isAbandoned = AtomicBoolean(false)

    init {
        Disposer.register(this, listener)
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener)
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
        var programmingLanguage: String? = null,
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
        isAbandoned.set(true)
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
        val metrics = metrics ?: return
        metrics.userDecision = decision

        if (metrics.requestId.isNotEmpty()) {
            telemetryHelper.recordInlineChatTelemetry(
                metrics.requestId,
                metrics.inputLength,
                metrics.numSelectedLines,
                metrics.codeIntent,
                metrics.userDecision,
                metrics.responseStartLatency,
                metrics.responseEndLatency,
                metrics.numSuggestionAddChars,
                metrics.numSuggestionAddLines,
                metrics.numSuggestionDelChars,
                metrics.numSuggestionDelLines,
                metrics.programmingLanguage
            )
        }
        this.metrics = null
    }

    private fun undoChanges() {
        scope.launch(EDT) {
            rejectAction?.invoke()
            rejectAction = null
            acceptAction = null
        }
    }

    private val diffAcceptHandler: () -> Unit = {
        scope.launch(EDT) {
            rejectAction = null
            acceptAction?.invoke()
            acceptAction = null
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
        val popup = InlineChatPopupFactory(
            acceptHandler = diffAcceptHandler,
            rejectHandler = { diffRejectHandler(editor) },
            submitHandler = popupSubmitHandler,
            cancelHandler = { popupCancelHandler(editor) }
        ).createPopup(editor, scope).also {
            currentPopup = it
        }

        addPopupListeners(popup, editor)
        Disposer.register(this, popup)
        canPopupAbort.set(true)
        val caretListener = createCaretListener(editor)
        editor.caretModel.addCaretListener(caretListener)
    }

    private fun createCaretListener(editor: Editor): CaretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            disposePopup(false)

            editor.caretModel.removeCaretListener(this)
        }
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
            backgroundColor = JBColor(0xAADEAA, 0x294436)
            effectColor = JBColor(0xAADEAA, 0x294436)
        }

        val redBackgroundAttributes = TextAttributes().apply {
            backgroundColor = JBColor(0xFFC8BD, 0x45302B)
            effectColor = JBColor(0xFFC8BD, 0x45302B)
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

    private fun processNewCode(editor: Editor, line: Int, event: ChatMessage, prevMessage: String) {
        if (isAbandoned.get()) return
        runBlocking {
            val code = event.message?.let { unescape(it) } ?: return@runBlocking
            logger.debug { "received inline chat recommendation with code: \n $code" }
            var insertLine = line
            var linesToAdd = emptyList<String>()
            val prevLines = prevMessage.split("\n")
            if (prevLines.size > 1 && code.startsWith(prevMessage)) {
                if (insertionLine.get() != -1) insertLine = insertionLine.get()
                linesToAdd = code.split("\n").drop(prevLines.size - 1)
            } else {
                linesToAdd = code.split("\n")
            }
            if (linesToAdd.last() == "") linesToAdd = linesToAdd.dropLast(1)
            val stringToAdd = if (linesToAdd.size > 1) linesToAdd.joinToString(separator = "\n") else linesToAdd.first()
            if (currentPopup?.isVisible != true) {
                logger.debug { "inline chat popup cancelled before diff is shown" }
                isInProgress.set(false)
                isAbandoned.set(true)
                recordInlineChatTelemetry(InlineChatUserDecision.DISMISS)
                return@runBlocking
            }
            withContext(EDT) {
                insertNewLineIfNeeded(insertLine, editor)
                insertString(editor, getLineStartOffset(editor.document, insertLine), stringToAdd + "\n")
            }
            insertLine += linesToAdd.size
            insertionLine.set(insertLine)
            acceptAction = {
                removeHighlighter(editor)
                try {
                    val caretPosition = CaretPosition(offset = getLineStartOffset(editor.document, line), line = line)
                    ReferenceLogController.addReferenceLog(code, event.codeReference, editor, project, inlineChatStartPosition = caretPosition)
                } catch (e: Exception) {
                    logger.warn { "error logging reference for inline chat: ${e.stackTraceToString()}" }
                }
            }
            rejectAction = {
                val startOffset = getLineStartOffset(editor.document, line)
                val endOffset = getLineEndOffset(editor.document, line + code.split("\n").size - 1)
                replaceString(editor.document, startOffset, endOffset, "")
                removeHighlighter(editor)
            }
            isInProgress.set(false)
            shouldShowActions.set(true)
        }
    }

    private fun processHighlights(diffRows: List<DiffRow>, startLine: Int, editor: Editor) {
        removeHighlighter(editor)
        var currentDocumentLine = startLine
        diffRows.forEach { row ->
            when (row.tag) {
                DiffRow.Tag.EQUAL -> {
                    currentDocumentLine++
                }
                DiffRow.Tag.DELETE -> {
                    val startOffset = getLineStartOffset(editor.document, currentDocumentLine)
                    val endOffset = getLineEndOffset(editor.document, currentDocumentLine, true)
                    highlightString(editor, startOffset, endOffset, false)
                    currentDocumentLine++
                }

                DiffRow.Tag.CHANGE -> {
                    val startOffset = getLineStartOffset(editor.document, currentDocumentLine)
                    val endOffset = getLineEndOffset(editor.document, currentDocumentLine, true)
                    highlightString(editor, startOffset, endOffset, false)
                    val insetStartOffset = getLineStartOffset(editor.document, currentDocumentLine + 1)
                    val insertEndOffset = getLineEndOffset(editor.document, currentDocumentLine + 1, true)
                    highlightString(editor, insetStartOffset, insertEndOffset, true)
                    currentDocumentLine += 2
                }

                DiffRow.Tag.INSERT -> {
                    val insetStartOffset = getLineStartOffset(editor.document, currentDocumentLine)
                    val insertEndOffset = getLineEndOffset(editor.document, currentDocumentLine, true)
                    highlightString(editor, insetStartOffset, insertEndOffset, true)
                    currentDocumentLine++
                }
            }
        }
    }

    private fun applyChunk(recommendation: String, editor: Editor, startLine: Int, endLine: Int) {
        val startOffset = getLineStartOffset(editor.document, startLine)
        val endOffset = getLineEndOffset(editor.document, endLine)
        replaceString(editor.document, startOffset, endOffset, recommendation)
    }

    private fun constructPatch(diff: List<DiffRow>): String {
        var patchString = ""
        diff.forEach { row ->
            when (row.tag) {
                DiffRow.Tag.EQUAL -> {
                    patchString += row.oldLine + "\n"
                }

                DiffRow.Tag.DELETE -> {
                    patchString += row.oldLine + "\n"
                }

                DiffRow.Tag.CHANGE -> {
                    patchString += row.oldLine + "\n"
                    patchString += row.newLine + "\n"
                }

                DiffRow.Tag.INSERT -> {
                    patchString += row.newLine + "\n"
                }
            }
        }
        return unescape(patchString)
    }

    private fun finalComputation(selectedCode: String, finalMessage: String?) {
        if (finalMessage == null) {
            canPopupAbort.set(true)
            throw Exception("No suggestions from Q; please try a different instruction.")
        }
        var numSuggestionAddChars = 0
        var numSuggestionAddLines = 0
        var numSuggestionDelChars = 0
        var numSuggestionDelLines = 0

        val selection = selectedCode.split("\n")
        val recommendationList = unescape(finalMessage).split("\n")
        val diff = compareDiffs(selection, recommendationList)
        var isAllEqual = true
        diff.forEach { row ->
            when (row.tag) {
                DiffRow.Tag.EQUAL -> {
                    // no-op
                }
                DiffRow.Tag.DELETE -> {
                    isAllEqual = false
                    numSuggestionDelLines += 1
                    numSuggestionDelChars += row.oldLine.length
                }

                DiffRow.Tag.CHANGE -> {
                    isAllEqual = false
                    numSuggestionDelLines += 1
                    numSuggestionDelChars += row.oldLine.length
                    numSuggestionAddLines += 1
                    numSuggestionAddChars = row.newLine.length
                }

                DiffRow.Tag.INSERT -> {
                    isAllEqual = false
                    numSuggestionAddLines += 1
                    numSuggestionAddChars += row.newLine.length
                }
            }
        }
        metrics?.numSuggestionAddChars = numSuggestionAddChars
        metrics?.numSuggestionAddLines = numSuggestionAddLines
        metrics?.numSuggestionDelChars = numSuggestionDelChars
        metrics?.numSuggestionDelLines = numSuggestionDelLines
        if (isAllEqual) {
            canPopupAbort.set(true)
            throw Exception("No suggestions from Q; please try a different instruction.")
        }
    }

    private fun processChatDiff(selectedCode: String, event: ChatMessage, editor: Editor, selectionRange: RangeMarker) {
        if (isAbandoned.get()) return
        if (event.message?.isNotEmpty() == true) {
            logger.info { "inline chat recommendation: \n ${event.message}" }
            runBlocking {
                val recommendation = unescape(event.message)
                val selection = selectedCode.split("\n")
                val recommendationList = recommendation.split("\n")
                val diff = compareDiffs(selection, recommendationList)
                val startLine = getLineNumber(editor.document, selectionRange.startOffset)
                val endLine = getLineNumber(editor.document, selectionRange.endOffset)
                val patchString = constructPatch(diff)
                if (currentPopup?.isVisible != true) {
                    logger.debug { "inline chat popup cancelled before diff is shown" }
                    isInProgress.set(false)
                    isAbandoned.set(true)
                    recordInlineChatTelemetry(InlineChatUserDecision.DISMISS)
                    return@runBlocking
                }
                withContext(EDT) {
                    removeSelection(editor)
                    applyChunk(patchString, editor, startLine, endLine)
                    processHighlights(diff, startLine, editor)
                }
                acceptAction = {
                    val startOffset = getLineStartOffset(editor.document, startLine)
                    val endOffset = getLineEndOffset(editor.document, max((startLine + patchString.split("\n").size - 1), endLine))
                    replaceString(editor.document, startOffset, endOffset, recommendation)
                    removeHighlighter(editor)
                    try {
                        val caretPosition =
                            CaretPosition(offset = selectionRange.startOffset, line = getLineNumber(editor.document, selectionRange.startOffset))
                        ReferenceLogController.addReferenceLog(recommendation, event.codeReference, editor, project, inlineChatStartPosition = caretPosition)
                    } catch (e: Exception) {
                        logger.warn { "error logging reference for inline chat: ${e.stackTraceToString()}" }
                    }
                }
                rejectAction = {
                    val startOffset = getLineStartOffset(editor.document, startLine)
                    val endOffset = getLineEndOffset(editor.document, max((startLine + patchString.split("\n").size - 1), endLine))
                    replaceString(editor.document, startOffset, endOffset, selectedCode)
                    removeHighlighter(editor)
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

    private fun getLineEndOffset(document: Document, row: Int, includeLastNewLine: Boolean = false): Int = ReadAction.compute<Int, Throwable> {
        if (row == document.lineCount - 1) {
            document.getLineEndOffset(row)
        } else if (row < document.lineCount - 1) {
            val lineEnd = document.getLineEndOffset(row)
            if (includeLastNewLine) lineEnd + 1 else lineEnd
        } else {
            document.getLineEndOffset((document.lineCount - 1).coerceAtLeast(0))
        }
    }

    private fun getLineNumber(document: Document, offset: Int): Int = ReadAction.compute<Int, Throwable> {
        document.getLineNumber(offset)
    }

    private fun insertString(editor: Editor, offset: Int, text: String): RangeMarker {
        lateinit var rangeMarker: RangeMarker

        broadcastQEvent(QFeatureEvent.STARTS_EDITING)
        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.insertString(offset, text)
                    rangeMarker = editor.document.createRangeMarker(offset, offset + text.length)
                }
                highlightCodeWithBackgroundColor(editor, rangeMarker.startOffset, rangeMarker.endOffset, true)
            }
        }
        broadcastQEvent(QFeatureEvent.FINISHES_EDITING)
        return rangeMarker
    }

    private fun replaceString(document: Document, start: Int, end: Int, text: String) {
        broadcastQEvent(QFeatureEvent.STARTS_EDITING)
        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    document.replaceString(start, end, text)
                }
            }
        }
        broadcastQEvent(QFeatureEvent.FINISHES_EDITING)
    }

    private fun highlightString(editor: Editor, start: Int, end: Int, isInsert: Boolean) {
        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    highlightCodeWithBackgroundColor(editor, start, end, isInsert)
                }
            }
        }
    }

    private fun removeHighlighter(editor: Editor) {
        ApplicationManager.getApplication().invokeAndWait {
            CommandProcessor.getInstance().runUndoTransparentAction {
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.markupModel.removeAllHighlighters()
                }
            }
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
                    QWebviewPanel.getInstance(project).browser?.prepareBrowser(BrowserState(FeatureId.AmazonQ))
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
        insertionLine.set(-1)
        isAbandoned.set(false)
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
        val intentRecognizer = UserIntentRecognizer()

        val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
        val fileContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ChatMessage)

        val tabId = UUID.randomUUID().toString()

        val requestData = ChatRequestData(
            tabId = tabId,
            message = message,
            activeFileContext = fileContext,
            userIntent = intentRecognizer.getUserIntentFromPromptChatMessage(message),
            triggerType = TriggerType.Inline,
            customization = CodeWhispererModelConfigurator.getInstance().activeCustomization(project),
            relevantTextDocuments = emptyList(),
            useRelevantDocuments = false
        )

        val sessionInfo = sessionStorage.getSession(tabId, project)
        val mutex = Mutex()
        val isReferenceAllowed = CodeWhispererSettings.getInstance().isIncludeCodeWithReference()

        var errorMessage = ""
        var prevMessage = ""
        val chat = sessionInfo.scope.async {
            ChatPromptHandler(telemetryHelper).handle(
                tabId,
                UUID.randomUUID().toString(),
                requestData,
                sessionInfo,
                shouldAddIndexInProgressMessage = false,
                isInlineChat = true
            )
                .catch { e ->
                    canPopupAbort.set(true)
                    undoChanges()
                    logger.warn { "Error in inline chat request: ${e.message}" }
                    errorMessage = "Error processing request; please try again"
                }
                .onEach { event: ChatMessage ->
                    if (event.message?.isNotEmpty() == true && prevMessage.isEmpty()) {
                        firstResponseLatency = (System.currentTimeMillis() - startTime).toDouble()
                    }
                    if (event.message?.isNotEmpty() == true && prevMessage != event.message) {
                        mutex.withLock {
                            if (event.codeReference?.isNotEmpty() == true && !isReferenceAllowed) {
                                canPopupAbort.set(true)
                                undoChanges()
                                errorMessage = "Suggestion had code reference; removed per setting."
                                return@withLock
                            }
                            try {
                                selectionRange?.let {
                                    processChatDiff(selectedCode, event, editor, it)
                                } ?: run {
                                    processNewCode(editor, selectedLineStart, event, prevMessage)
                                }
                            } catch (e: Exception) {
                                canPopupAbort.set(true)
                                undoChanges()
                                logger.warn { "error streaming chat message to editor: ${e.stackTraceToString()}" }
                                errorMessage = "Error processing request; please try again."
                            }
                            prevMessage = unescape(event.message)
                        }
                    }
                    messages.add(event)
                }
                .toList()
        }
        chat.await()
        val finalMessage = messages.lastOrNull { m -> m.messageType == ChatMessageType.AnswerPart }
        val lastResponseLatency = (System.currentTimeMillis() - startTime).toDouble()
        val requestId = messages.lastOrNull()?.messageId
        requestId?.let {
            metrics = InlineChatMetrics(
                requestId = it, inputLength = message.length, numSelectedLines = selectedCode.split("\n").size,
                codeIntent = true, responseStartLatency = firstResponseLatency, responseEndLatency = lastResponseLatency,
                programmingLanguage = fileContext.fileContext?.fileLanguage
            )
        }
        if (finalMessage != null) {
            try {
                finalComputation(selectedCode, finalMessage.message)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error processing request; please try again."
            }
        }
        if (errorMessage.isNotEmpty()) {
            canPopupAbort.set(true)
            undoChanges()
        }

        broadcastQEvent(QFeatureEvent.FINISHES_EDITING)
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
