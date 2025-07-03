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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import org.eclipse.lsp4j.jsonrpc.messages.Either
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
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
import software.aws.toolkits.jetbrains.utils.isQConnected
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
                    CodeWhispererCodeReferenceManager.getInstance(it.project).insertCodeReference(editor, it.item, startOffset)
                    val importAdder = CodeWhispererImportAdder.getFallback()
                    if (importAdder == null) {
                        logInline(triggerSessionId) {
                            "No import adder found for JB inline"
                        }
                        return
                    }
                    importAdder.insertImportStatements(it.project, editor, it.item?.mostRelevantMissingImports)
                    logInline(triggerSessionId) {
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
    private val activeTriggerSessions = mutableMapOf<Int, InlineCompletionSessionContext>()

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

    private fun isValidVariant(variant: InlineCompletionVariant.Snapshot) =
        (
            (!variant.isEmpty() && variant.elements.any { it.text.isNotEmpty() }) ||
                variant.data.getUserData(KEY_Q_INLINE_ITEM_CONTEXT)?.item != null
            ) &&
            variant.state != InlineCompletionVariant.Snapshot.State.INVALIDATED

    companion object {
        private const val MAX_CHANNELS = 5
        private val LOG = getLogger<QInlineCompletionProvider>()
        val Q_INLINE_PROVIDER_ID = InlineCompletionProviderID("Amazon Q")
        val KEY_Q_INLINE_ITEM_CONTEXT = Key<InlineCompletionItemContext>("amazon.q.inline.completion.item.context")

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
        sessionContext: InlineCompletionSessionContext,
    ) {
        handler.addEventListener(
            object : InlineCompletionEventAdapter {
                // when all computations are done
                override fun onCompletion(event: InlineCompletionEventType.Completion) {
                    CodeWhispererInvocationStatus.getInstance().setIsInvokingQInline(session, false)
                    updateDisplayIndex(session)
                    logInline(triggerSessionId) {
                        "Session pagination progress is complete, now showing suggestions"
                    }
                    super.onCompletion(event)
                }

                override fun onComputed(event: InlineCompletionEventType.Computed) {
                    updateDisplayIndex(session)
                    super.onComputed(event)
                }

                override fun onShow(event: InlineCompletionEventType.Show) {
                    val isFirstTimeShowing = sessionContext.itemContexts.all { !it.hasSeen }
                    setSelectedVariantAsSeen(sessionContext, event.variantIndex)
                    if (isFirstTimeShowing) {
                        CodeWhispererInvocationStatus.getInstance().completionShown()
                        latencyContext.codewhispererEndToEndEnd = System.nanoTime()

                        // For JB inline completion UX, no userInput
                        latencyContext.perceivedLatency = latencyContext.getCodeWhispererEndToEndLatency()

                        logInline(triggerSessionId) {
                            "Session first time showing, perceived E2E latency: ${latencyContext.perceivedLatency}"
                        }
                    }
                    super.onShow(event)
                }

                override fun onInvalidated(event: InlineCompletionEventType.Invalidated) {
                    updateDisplayIndex(session)
                    super.onInvalidated(event)
                }

                override fun onEmpty(event: InlineCompletionEventType.Empty) {
                    setSelectedVariantAsDiscarded(sessionContext, event.variantIndex)
                    setSelectedVariantAsSeen(sessionContext, event.variantIndex, seen = false)
                    super.onEmpty(event)
                }

                override fun onVariantSwitched(event: InlineCompletionEventType.VariantSwitched) {
                    updateDisplayIndex(session, event.toVariantIndex - event.fromVariantIndex)
                    setSelectedVariantAsSeen(sessionContext, event.toVariantIndex)
                    logInline(triggerSessionId) {
                        "Switching to variant ${event.toVariantIndex}"
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
                            currentAcceptedItemContext = session.capture()?.activeVariant?.data?.getUserData(KEY_Q_INLINE_ITEM_CONTEXT)
                            insertHandler.afterTyped(editor, sessionContext.triggerOffset)
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
                            // TODO: for current version, always send discard event for invalidated sessions, language server
                            // also sends a discarded event on their side. In a future version
                            // we will support concurrent triggers first in language server and then here.
                            // as of now, for INVALIDATED JB can send discard to language server regardless
                            val atLeastOneSeen = sessionContext.itemContexts.any { it.hasSeen }
                            if (!atLeastOneSeen) {
                                repeat(sessionContext.itemContexts.size) { i ->
                                    setSelectedVariantAsDiscarded(sessionContext, i)
                                }
                            }
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
                        CodeWhispererTelemetryService.getInstance().sendUserTriggerDecisionEventForTriggerSession(
                            project,
                            latencyContext,
                            sessionContext,
                            triggerSessionId,
                        )
                        activeTriggerSessions.remove(triggerSessionId)
                    }
                }
            },
            session
        )
    }

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
                } else if (event.isManualCall()) {
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

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val editor = request.editor
        val document = editor.document
        val project = editor.project ?: return InlineCompletionSuggestion.Empty
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return InlineCompletionSuggestion.Empty
        val session = InlineCompletionSession.getOrNull(editor) ?: return InlineCompletionSuggestion.Empty
        val triggerSessionId = triggerSessionId++
        val latencyContext = LatencyContext(codewhispererEndToEndStart = System.nanoTime())
        val triggerTypeInfo = getTriggerTypeInfo(request)

        CodeWhispererInvocationStatus.getInstance().setIsInvokingQInline(session, true)
        Disposer.register(session) {
            CodeWhispererInvocationStatus.getInstance().setIsInvokingQInline(session, false)
        }

        // this is only available in 2024.3+
        if (request.event.isDeletion()) {
            logInline(triggerSessionId) {
                "Skip inline completion when deleting"
            }
            return InlineCompletionSuggestion.Empty
        }

        val sessionContext = InlineCompletionSessionContext(triggerOffset = request.endOffset)

        // Pagination workaround: Always return exactly 5 variants
        // Create channel placeholder for upcoming pagination results
        // this is the only known way paginated items can show later
        repeat(MAX_CHANNELS) {
            sessionContext.itemContexts.add(InlineCompletionItemContext(project, null, Channel(Channel.UNLIMITED)))
        }

        activeTriggerSessions[triggerSessionId] = sessionContext

        addQInlineCompletionListener(project, editor, session, handler, triggerSessionId, latencyContext, sessionContext)

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
            // Launch coroutine for background pagination progress
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
                    "Pagination finished, closing all channels"
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

            return object : InlineCompletionSuggestion {
                override suspend fun getVariants(): List<InlineCompletionVariant> =
                    sessionContext.itemContexts.map { itemContext ->
                        itemContext.data.putUserData(KEY_Q_INLINE_ITEM_CONTEXT, itemContext)
                        InlineCompletionVariant.build(data = itemContext.data, elements = itemContext.channel.receiveAsFlow())
                    }
            }
        } catch (e: Exception) {
            logInline(triggerSessionId, e) {
                "Error getting inline completion suggestion: ${e.message}"
            }
            if (e is CancellationException) {
                throw e
            }
            return InlineCompletionSuggestion.Empty
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
                "Received ${nextPageResult.items.size} items from pagination with token: ${nextToken?.left}"
            }

            // Update channels in order with new items from pagination
            if (nextPageResult.sessionId.isEmpty()) {
                activeTriggerSessions.remove(triggerSessionId)
                logInline(triggerSessionId) {
                    "Ending pagination progress since LSP returned EMPTY_RESULT"
                }
                return null
            }

            sessionContext.sessionId = nextPageResult.sessionId
            nextPageResult.items.forEachIndexed { itemIndex, newItem ->
                // Calculate which channel this item should go to, continue from where we left off
                val channelIndex = sessionContext.counter % MAX_CHANNELS
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
                val success = existingChannel.trySend(InlineCompletionGrayTextElement(displayText))
                logInline(triggerSessionId) {
                    "Adding paginated item '${newItem.itemId}' to channel $channelIndex, success: ${success.isSuccess}, discarded: $discarded"
                }
                sessionContext.counter++
            }
            return nextPageResult.partialResultToken
        } catch (e: Exception) {
            logInline(triggerSessionId, e) {
                "Error during pagination"
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

        if (!isQConnected(project)) return false
        if (event.isManualCall()) return true
        if (!CodeWhispererExplorerActionManager.getInstance().isAutoEnabled()) return false
        if (QRegionProfileManager.getInstance().hasValidConnectionButNoActiveProfile(project)) return false
        return true
    }
}
