// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionInsertEnvironment
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderPresentation
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.util.preferredHeight
import icons.AwsIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.importadder.CodeWhispererImportAdder
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InlineCompletionItemContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InlineCompletionSessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getDocumentDiagnostics
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel

class QInlineCompletionProvider(private val cs: CoroutineScope) : InlineCompletionProvider {
    // TODO: good news is that suggestionUpdateManager can be overridden, this means we can define custom behaviors
    // such as on backspace when variants are showing, future improvements
    override val suggestionUpdateManager: InlineCompletionSuggestionUpdateManager
        get() = super.suggestionUpdateManager
    override val insertHandler: QInlineCompletionInsertHandler
        get() = object : QInlineCompletionInsertHandler {
            override fun afterTyped(editor: Editor, startOffset: Int) {
                insertCodeReferencesAndImports(editor, startOffset)
            }

            override fun afterInsertion(environment: InlineCompletionInsertEnvironment, elements: List<InlineCompletionElement>) {
                insertCodeReferencesAndImports(environment.editor, environment.insertedRange.startOffset)
            }

            private fun insertCodeReferencesAndImports(editor: Editor, startOffset: Int) {
                currentAcceptedItemContext?.let {
                    val currentTriggerSessionId = triggerSessionId - 1
                    CodeWhispererCodeReferenceManager.getInstance(it.project).insertCodeReference(editor, it.item, startOffset)
                    val importAdder = CodeWhispererImportAdder.getFallback()
                    if (importAdder == null) {
                        logInline(currentTriggerSessionId) {
                            "No import adder found for JB inline"
                        }
                        return
                    }
                    importAdder.insertImportStatements(it.project, editor, it.item?.mostRelevantMissingImports)
                    logInline(currentTriggerSessionId) {
                        "Accepted suggestion has ${it.item?.references?.size ?: 0} references and " +
                            "${it.item?.mostRelevantMissingImports?.size ?: 0} imports"
                    }
                }
            }
        }
    override val id: InlineCompletionProviderID = Q_INLINE_PROVIDER_ID
    override val providerPresentation: InlineCompletionProviderPresentation
        get() = object : InlineCompletionProviderPresentation {
            override fun getTooltip(project: Project?): JComponent {
                project ?: return JLabel()
                val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return JLabel()
                val session = InlineCompletionSession.getOrNull(editor) ?: return JLabel()
                return qToolTip(
                    "Amazon Q",
                    AwsIcons.Logos.AWS_Q_GRADIENT_SMALL,
                    session
                )
            }
        }
    private var cell: Cell<JEditorPane>? = null
    private var triggerSessionId = 0
    private var currentAcceptedItemContext: InlineCompletionItemContext? = null

    // not needed for current implementation, will need this when we support concurrent triggers, so leave it here
    private val activeTriggerSessions = linkedMapOf<Int, InlineCompletionSessionContext>()

    fun qToolTip(
        title: String,
        icon: Icon,
        session: InlineCompletionSession,
    ): JComponent {
        return panel {
            row {
                icon(icon).gap(RightGap.SMALL)
                comment(title).gap(RightGap.SMALL)
                cell(navigationButton(session, "←")).gap(RightGap.SMALL)
                text(indexDisplayText(session)).gap(RightGap.SMALL).applyToComponent {
                    // workaround of text cutoff issue: 21 is the max width of all its possible strings (4/5)
                    this.preferredSize = Dimension(21, this.preferredHeight)
                }.also {
                    cell = it
                }
                cell(navigationButton(session, "→"))

                // if there are imports and references, add them here
                val item = session.capture()?.activeVariant?.data?.getUserData(KEY_Q_INLINE_ITEM_CONTEXT)?.item ?: return@row
                val imports = item.mostRelevantMissingImports
                val references = item.references

                if (!imports.isNullOrEmpty()) {
                    cell(
                        JLabel("${imports.size} imports").apply {
                            toolTipText = "<html>${imports.joinToString("<br>") { it.statement }}</html>"
                        }
                    )
                }
                val components = CodeWhispererPopupManager.getInstance().popupComponents
                if (!references.isNullOrEmpty()) {
                    cell(JLabel("Reference code under ")).customize(UnscaledGaps.EMPTY)
                    references.forEachIndexed { i, reference ->
                        cell(components.licenseLink(reference.licenseName)).customize(UnscaledGaps.EMPTY)
                        if (i == references.size - 1) return@forEachIndexed
                        cell(JLabel(", ")).customize(UnscaledGaps.EMPTY)
                    }
                }
            }
        }
    }

    private fun getDisplayVariantIndex(session: InlineCompletionSession, direction: Int) =
        getCurrentValidVariantIndex(session, direction) + 1

    private fun getCurrentValidVariantIndex(session: InlineCompletionSession, direction: Int): Int {
        val variants = session.capture()?.variants ?: return -1
        // the variants snapshots at this point either 1) have elements(normal case) 2) have no elements but have
        // data (trySend(elements) hasn't finished), so need to check both
        val validVariants = variants.filter { isValidVariant(it) }
        if (validVariants.isEmpty()) return 0
        return (validVariants.indexOfFirst { it.isActive } + direction + validVariants.size) % validVariants.size
    }

    fun getAllValidVariantsCount(session: InlineCompletionSession) =
        session.capture()?.variants?.count { isValidVariant(it) } ?: 0

    private fun isValidVariant(variant: InlineCompletionVariant.Snapshot): Boolean {
        val itemContext = variant.data.getUserData(KEY_Q_INLINE_ITEM_CONTEXT) ?: return false
        return (
            (!variant.isEmpty() && variant.elements.any { it.text.isNotEmpty() }) ||
                itemContext.item != null
            ) &&
            variant.state != InlineCompletionVariant.Snapshot.State.INVALIDATED &&
            !itemContext.isDiscarded
    }

    companion object {
        // 5 paginated results per trigger
        private const val MAX_CHANNELS_PER_TRIGGER = 5

        // JB inline api doesn't allow displaying more than 20 suggestions per display session
        private const val MAX_CHANNELS_PER_DISPLAY_SESSION = 20

        private val LOG = getLogger<QInlineCompletionProvider>()
        val Q_INLINE_PROVIDER_ID = InlineCompletionProviderID("Amazon Q")
        val KEY_Q_INLINE_ITEM_CONTEXT = Key<InlineCompletionItemContext>("amazon.q.inline.completion.item.context")

        // used in src-242/QManualCall.kt
        val DATA_KEY_Q_AUTO_TRIGGER_INTELLISENSE = DataKey.create<Boolean>("amazon.q.auto.trigger.intellisense")
        val KEY_Q_AUTO_TRIGGER_INTELLISENSE = Key<Boolean>("amazon.q.auto.trigger.intellisense")

        fun invokeCompletion(editor: Editor, isIntelliSenseAccept: Boolean = false) {
            val event = getManualCallEvent(editor, isIntelliSenseAccept)
            InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(event)
        }

        fun logInline(triggerSessionId: Int, e: Throwable? = null, block: () -> String) {
            LOG.debug(e) { "Q inline: Trigger session $triggerSessionId: ${block()}" }
        }
    }

    private fun addQInlineCompletionListener(
        project: Project,
        editor: Editor,
        session: InlineCompletionSession,
        handler: InlineCompletionHandler,
        triggerSessionId: Int,
        latencyContext: LatencyContext,
    ) {
        handler.addEventListener(
            object : InlineCompletionEventAdapter {
                // when all computations are done (all channels are closed)
                override fun onCompletion(event: InlineCompletionEventType.Completion) {
                    CodeWhispererInvocationStatus.getInstance().setIsInvokingQInline(session, false)
                    updateDisplayIndex(session)
                    logInline(triggerSessionId) {
                        "Session pagination progress is complete"
                    }
                    super.onCompletion(event)
                }

                override fun onComputed(event: InlineCompletionEventType.Computed) {
                    updateDisplayIndex(session)
                    super.onComputed(event)
                }

                // when any suggestion is shown (any channel is closed)
                override fun onShow(event: InlineCompletionEventType.Show) {
                    val targetSessionContext = getSessionContextFromVariantIndex(event.variantIndex) ?: return
                    val targetVariantIndex = getSessionIndexFromVariantIndex(event.variantIndex)
                    val isFirstTimeShowing = activeTriggerSessions.values.all { eachSessionContext ->
                        eachSessionContext.itemContexts.all { eachItemContext -> !eachItemContext.hasSeen }
                    }
                    setSelectedVariantAsSeen(targetSessionContext, targetVariantIndex)
                    if (isFirstTimeShowing) {
                        CodeWhispererInvocationStatus.getInstance().completionShown()
                        latencyContext.codewhispererEndToEndEnd = System.nanoTime()

                        // For JB inline completion UX, no userInput
                        latencyContext.perceivedLatency = latencyContext.getCodeWhispererEndToEndLatency()

                        logInline(triggerSessionId) {
                            "Start showing suggestions for the current display session from $triggerSessionId, " +
                                "perceived E2E latency: ${latencyContext.perceivedLatency}"
                        }
                    }
                    super.onShow(event)
                }

                override fun onInvalidated(event: InlineCompletionEventType.Invalidated) {
                    updateDisplayIndex(session)
                    super.onInvalidated(event)
                }

                override fun onEmpty(event: InlineCompletionEventType.Empty) {
                    val targetSessionContext = getSessionContextFromVariantIndex(event.variantIndex) ?: return
                    val targetVariantIndex = getSessionIndexFromVariantIndex(event.variantIndex)
                    setSelectedVariantAsDiscarded(targetSessionContext, targetVariantIndex)
                    setSelectedVariantAsSeen(targetSessionContext, targetVariantIndex, seen = false)
                    super.onEmpty(event)
                }

                override fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {
                    val targetSessionContext = getSessionContextFromVariantIndex(event.toVariantIndex) ?: return
                    val targetVariantIndex = getSessionIndexFromVariantIndex(event.toVariantIndex)
                    updateDisplayIndex(session, event.toVariantIndex - event.fromVariantIndex)
                    setSelectedVariantAsSeen(targetSessionContext, targetVariantIndex)
                    logInline(triggerSessionId) {
                        "Switching to session ${targetSessionContext.sessionId} variant $targetVariantIndex"
                    }
                    super.onVariantSwitched(event)
                }

                override fun onInsert(event: InlineCompletionEventType.Insert) {
                    currentAcceptedItemContext = session.capture()?.activeVariant?.data?.getUserData(KEY_Q_INLINE_ITEM_CONTEXT)
                }

                override fun onHide(event: InlineCompletionEventType.Hide) {
                    logInline(triggerSessionId) {
                        "Exiting display session with reason: ${event.finishType}"
                    }
                    super.onHide(event)
                    when (event.finishType) {
                        InlineCompletionUsageTracker.ShownEvents.FinishType.CARET_CHANGED,
                        InlineCompletionUsageTracker.ShownEvents.FinishType.MOUSE_PRESSED,
                        InlineCompletionUsageTracker.ShownEvents.FinishType.EDITOR_REMOVED,
                        InlineCompletionUsageTracker.ShownEvents.FinishType.ESCAPE_PRESSED,
                        InlineCompletionUsageTracker.ShownEvents.FinishType.BACKSPACE_PRESSED,
                        -> {
                            setCurrentVariantAsRejected(session)
                        }
                        InlineCompletionUsageTracker.ShownEvents.FinishType.TYPED -> {
                            // TYPED finished type will not trigger insert hook from JB (to insert imports and references) so have to manually set and invoke here
                            val variant = session.capture()?.activeVariant ?: return

                            val targetSessionContext = getSessionContextFromVariantIndex(variant.index) ?: return
                            currentAcceptedItemContext = variant.data.getUserData(KEY_Q_INLINE_ITEM_CONTEXT)
                            insertHandler.afterTyped(editor, targetSessionContext.triggerOffset)
                            setCurrentVariantAsAccepted(session)
                        }
                        InlineCompletionUsageTracker.ShownEvents.FinishType.EMPTY -> {
                            if (session.request.event.isManualCall()) {
                                runInEdt {
                                    HintManager.getInstance().showInformationHint(
                                        session.editor,
                                        message("codewhisperer.popup.no_recommendations"),
                                        HintManager.UNDER
                                    )
                                }
                            }
                            // language server returns EMPTY_RESULT, no need to send any telemetry.
                        }
                        InlineCompletionUsageTracker.ShownEvents.FinishType.INVALIDATED -> {
                            // For the current blocking trigger logic, when using JB inline API,
                            // previous display session will always be disposed when there's a new one
                            // So we will display any valid results in the newer display session
                            // Don't yet send UTD telemetry for all the suggestions because they
                            // can be displayed later(still in activeTriggerSessions map),
                            // by then we will send telemetry for them.
                            val atLeastOneSeen = activeTriggerSessions.values.any {
                                it.itemContexts.any { itemContext -> itemContext.hasSeen }
                            }
                            // when invalidated by typing we need to send UTD for all active trigger sessions
                            if (!atLeastOneSeen) return
                        }
                        InlineCompletionUsageTracker.ShownEvents.FinishType.SELECTED -> {
                            setCurrentVariantAsAccepted(session)
                        }
                        else -> {
                            logInline(triggerSessionId) {
                                "Closing session for unchecked reason: ${event.finishType}"
                            }
                        }
                    }
                    cs.launch {
                        val copy = activeTriggerSessions.toMap()
                        activeTriggerSessions.clear()
                        logInline(triggerSessionId) {
                            "activeTriggerSessions map cleared"
                        }
                        copy.forEach { (t, u) ->
                            CodeWhispererTelemetryService.getInstance().sendUserTriggerDecisionEventForTriggerSession(
                                project,
                                latencyContext,
                                u,
                                t,
                                editor.document
                            )
                        }
                    }
                }
            },
            session
        )
    }

    private fun getSessionContextFromVariantIndex(variantIndex: Int): InlineCompletionSessionContext? {
        val sessionIndex = variantIndex / MAX_CHANNELS_PER_TRIGGER
        if (sessionIndex >= activeTriggerSessions.size) return null
        return activeTriggerSessions[activeTriggerSessions.keys.toList()[sessionIndex]]
    }

    private fun getSessionIndexFromVariantIndex(variantIndex: Int) = variantIndex % MAX_CHANNELS_PER_TRIGGER

    private fun updateDisplayIndex(session: InlineCompletionSession, steps: Int = 0) {
        cell?.text(indexDisplayText(session, steps))
    }

    private fun indexDisplayText(session: InlineCompletionSession, steps: Int = 0) =
        "${getDisplayVariantIndex(session, steps)}/${getAllValidVariantsCount(session)}"

    private fun setCurrentVariantAsAccepted(session: InlineCompletionSession, isAccepted: Boolean = true) {
        session.capture()?.activeVariant?.data?.getUserData(KEY_Q_INLINE_ITEM_CONTEXT)?.isAccepted = isAccepted
    }

    private fun setCurrentVariantAsRejected(session: InlineCompletionSession) {
        setCurrentVariantAsAccepted(session, false)
    }

    private fun setSelectedVariantAsSeen(sessionContext: InlineCompletionSessionContext, index: Int, seen: Boolean = true) {
        sessionContext.itemContexts[index].hasSeen = seen
    }

    private fun setSelectedVariantAsDiscarded(sessionContext: InlineCompletionSessionContext, index: Int) {
        sessionContext.itemContexts[index].isDiscarded = true
    }

    private fun getTriggerTypeInfo(request: InlineCompletionRequest): TriggerTypeInfo {
        val event = request.event
        val triggerType = if (event.isManualCall()) CodewhispererTriggerType.OnDemand else CodewhispererTriggerType.AutoTrigger
        val automatedTriggerType = when (triggerType) {
            CodewhispererTriggerType.AutoTrigger -> {
                val triggerString = request.document.charsSequence.substring(request.startOffset, request.endOffset)
                if (triggerString.startsWith(System.lineSeparator())) {
                    CodeWhispererAutomatedTriggerType.Enter()
                } else if (CodeWhispererConstants.SPECIAL_CHARACTERS_LIST.contains(triggerString)) {
                    CodeWhispererAutomatedTriggerType.SpecialChar(triggerString.single())
                } else if (event.isIntelliSense()) {
                    CodeWhispererAutomatedTriggerType.IntelliSense()
                } else {
                    CodeWhispererAutomatedTriggerType.Classifier()
                }
            }
            CodewhispererTriggerType.OnDemand,
            CodewhispererTriggerType.Unknown,
            -> {
                CodeWhispererAutomatedTriggerType.Unknown()
            }
        }
        return TriggerTypeInfo(triggerType, automatedTriggerType)
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion = withContext(NonCancellable) {
        val editor = request.editor
        val project = editor.project ?: return@withContext InlineCompletionSuggestion.Empty

        // try to refresh automatically if possible, otherwise ask user to login again
        if (isQExpired(project)) {
            // consider changing to only running once a ~minute since this is relatively expensive
            // say the connection is un-refreshable if refresh fails for 3 times
            val shouldReauth = withContext(getCoroutineBgContext()) {
                CodeWhispererUtil.promptReAuth(project)
            }

            if (shouldReauth) {
                return@withContext InlineCompletionSuggestion.Empty
            }
        }

        val document = editor.document
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return@withContext InlineCompletionSuggestion.Empty
        val session = InlineCompletionSession.getOrNull(editor) ?: return@withContext InlineCompletionSuggestion.Empty
        val triggerSessionId = triggerSessionId++
        val latencyContext = LatencyContext(codewhispererEndToEndStart = System.nanoTime())
        val triggerTypeInfo = getTriggerTypeInfo(request)
        val diagnostics = getDocumentDiagnostics(editor.document, project)

        CodeWhispererInvocationStatus.getInstance().setIsInvokingQInline(session, true)
        Disposer.register(session) {
            CodeWhispererInvocationStatus.getInstance().setIsInvokingQInline(session, false)
        }

        val sessionContext = InlineCompletionSessionContext(triggerOffset = request.endOffset, diagnostics = diagnostics)

        // Pagination workaround: Always return exactly 5 variants
        // Create channel placeholder for upcoming pagination results
        // this is the only known way paginated items can show later
        repeat(MAX_CHANNELS_PER_TRIGGER) {
            sessionContext.itemContexts.add(InlineCompletionItemContext(project, null, Channel(Channel.UNLIMITED)))
        }

        activeTriggerSessions[triggerSessionId] = sessionContext

        addQInlineCompletionListener(project, editor, session, handler, triggerSessionId, latencyContext)

        runReadAction {
            logInline(triggerSessionId) {
                "Initializing trigger session, " +
                    "event type: ${request.event.javaClass.simpleName}, " +
                    "startOffset: ${request.startOffset}, " +
                    "endOffset: ${request.endOffset}, " +
                    "trigger text: ${document.charsSequence.subSequence(request.startOffset, request.endOffset)}, " +
                    "triggerTypeInfo: $triggerTypeInfo, " +
                    "left context of the current line: ${
                        document.charsSequence.subSequence(
                            document.getLineStartOffset(document.getLineNumber(editor.caretModel.offset)),
                            editor.caretModel.offset
                        )
                    }"
            }
        }

        try {
            // Start pagination asynchronously to avoid blocking
            cs.launch {
                var nextToken: Either<String, Int>? = null
                do {
                    nextToken = startPaginationInBackground(
                        project,
                        editor,
                        triggerTypeInfo,
                        triggerSessionId,
                        nextToken,
                        sessionContext,
                    )
                } while (nextToken != null && !nextToken.left.isNullOrEmpty())

                // closing all channels since pagination for this session has finished
                logInline(triggerSessionId) {
                    "Pagination finished, closing remaining empty channels"
                }
                sessionContext.itemContexts.forEach {
                    it.channel.close()
                }
                if (session.context.isDisposed) {
                    logInline(triggerSessionId) {
                        "Current display session already disposed by a new trigger before pagination finishes, exiting"
                    }
                }
            }

            return@withContext object : InlineCompletionSuggestion {
                override suspend fun getVariants(): List<InlineCompletionVariant> {
                    // also need to build valid elements from last session
                    val result = mutableListOf<InlineCompletionVariant>()
                    activeTriggerSessions.forEach { (t, u) ->
                        logInline(triggerSessionId) {
                            "Adding results from previous trigger $t for the current display session"
                        }
                        result.addAll(
                            u.itemContexts.map { itemContext ->
                                itemContext.data.putUserData(KEY_Q_INLINE_ITEM_CONTEXT, itemContext)
                                InlineCompletionVariant.build(data = itemContext.data, elements = itemContext.channel.receiveAsFlow())
                            }.take((MAX_CHANNELS_PER_DISPLAY_SESSION - result.size).coerceAtLeast(0))
                        )
                    }
                    return result
                }
            }
        } catch (e: Exception) {
            logInline(triggerSessionId, e) {
                "Error getting inline completion suggestion: ${e.message}"
            }
            if (e is CancellationException) {
                throw e
            }
            return@withContext InlineCompletionSuggestion.Empty
        }
    }

    private suspend fun startPaginationInBackground(
        project: Project,
        editor: Editor,
        triggerTypeInfo: TriggerTypeInfo,
        triggerSessionId: Int,
        nextToken: Either<String, Int>?,
        sessionContext: InlineCompletionSessionContext,
    ): Either<String, Int>? {
        try {
            logInline(triggerSessionId) {
                "Fetching next paginated results with token: ${nextToken?.left}"
            }

            val nextPageResult = AmazonQLspService.executeAsyncIfRunning(project) { server ->
                val params = createInlineCompletionParams(editor, triggerTypeInfo, nextToken)
                server.inlineCompletionWithReferences(params)
            }?.await() ?: run {
                logInline(triggerSessionId) {
                    "Received null completion response from LSP, stop pagination for the current session"
                }
                return null
            }

            logInline(triggerSessionId) {
                "Received ${nextPageResult.items.size} items from pagination with current token: ${nextToken?.left}, " +
                    "next token: ${nextPageResult.partialResultToken?.left}"
            }

            // Update channels in order with new items from pagination
            if (nextPageResult.sessionId.isEmpty()) {
                activeTriggerSessions.remove(triggerSessionId)
                logInline(triggerSessionId) {
                    "Skipping UserTriggerDecision for trigger session: $triggerSessionId. " +
                        "Ending pagination progress since invocation didn't happen in LSP and LSP returned EMPTY_RESULT, "
                }
                return null
            }

            sessionContext.sessionId = nextPageResult.sessionId
            nextPageResult.items.forEachIndexed { itemIndex, newItem ->
                // Calculate which channel this item should go to, continue from where we left off
                val channelIndex = sessionContext.counter % MAX_CHANNELS_PER_TRIGGER
                if (channelIndex >= sessionContext.itemContexts.size) {
                    // service returned more than 5 suggestions, we don't have enough channel placeholders
                    return@forEachIndexed
                }
                sessionContext.itemContexts[channelIndex].item = newItem
                val existingChannel = sessionContext.itemContexts[channelIndex].channel

                // try displaying the suggestions in the current session, if it's still valid
                var discarded = false
                val displayText =
                    runReadAction {
                        if (editor.caretModel.offset < sessionContext.triggerOffset) {
                            discarded = true
                            ""
                        } else {
                            val userInput = editor.document.charsSequence.subSequence(sessionContext.triggerOffset, editor.caretModel.offset)
                            if (newItem.insertText.startsWith(userInput)) {
                                newItem.insertText.substring(userInput.length)
                            } else {
                                discarded = true
                                ""
                            }
                        }
                    }

                if (newItem.insertText.isEmpty()) {
                    logInline(triggerSessionId) {
                        "Received variant ${channelIndex + 1} as an empty string"
                    }
                }

                sessionContext.itemContexts[channelIndex].isDiscarded = discarded
                val isSuccess =
                    if (displayText.isEmpty()) {
                        false
                    } else {
                        existingChannel.trySend(InlineCompletionGrayTextElement(displayText)).isSuccess
                    }
                logInline(triggerSessionId) {
                    "Adding paginated item '${newItem.itemId}' to channel $channelIndex, " +
                        "original first line context: ${newItem.insertText.lines()[0]}, " +
                        "success: $isSuccess, discarded: $discarded. Closing"
                }
                existingChannel.close()
                sessionContext.counter++
            }
            return nextPageResult.partialResultToken
        } catch (e: Exception) {
            logInline(triggerSessionId, e) {
                "Error during pagination"
            }
            if (e is ResponseErrorException) {
                // convoluted but lines up with "The bearer token included in the request is invalid"
                // https://github.com/aws/language-servers/blob/1f3e93024eeb22186a34f0bd560f8d552f517300/server/aws-lsp-codewhisperer/src/language-server/chat/utils.ts#L22-L23
                // error data is nullable
                if (e.responseError.data?.toString()?.contains("E_AMAZON_Q_CONNECTION_EXPIRED") == true) {
                    try {
                        // kill the session if the connection is expired
                        val connection = ToolkitConnectionManager
                            .getInstance(project)
                            .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
                        connection?.let { it.getConnectionSettings().tokenProvider.delegate as? BearerTokenProvider }
                            ?.invalidate()
                    } catch (e: Exception) {
                        LOG.warn(e) { "Failed to invalidate existing token in response to E_AMAZON_Q_CONNECTION_EXPIRED" }
                    }
                    CodeWhispererUtil.reconnectCodeWhisperer(project)
                }
            }
            return null
        }
    }

    private fun createInlineCompletionParams(
        editor: Editor,
        triggerTypeInfo: TriggerTypeInfo,
        nextToken: Either<String, Int>?,
    ) = CodeWhispererService.getInstance().createInlineCompletionParams(
        editor,
        triggerTypeInfo,
        nextToken
    )

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        val request = event.toRequest() ?: return false
        val editor = request.editor
        val project = editor.project ?: return false

        // qExpired case handled in completion handler
        if (!isQConnected(project)) return false
        if (QRegionProfileManager.getInstance().hasValidConnectionButNoActiveProfile(project)) return false
        if (event.isManualCall()) return true
        if (!CodeWhispererExplorerActionManager.getInstance().isAutoEnabled()) return false
        if (request.event.isDeletion()) return false
        return true
    }
}
