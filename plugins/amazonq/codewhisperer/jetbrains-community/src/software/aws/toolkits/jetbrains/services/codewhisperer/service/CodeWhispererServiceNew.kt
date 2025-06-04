// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import software.amazon.awssdk.core.exception.SdkServiceException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionContext
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionTriggerKind
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionWithReferencesParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.getCaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.WorkerContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CaretMovement
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeInsightsSettingsFacade
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCompletionType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.notifyErrorCodeWhispererUsageLimit
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.aws.toolkits.jetbrains.utils.isInjectedText
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.util.concurrent.TimeUnit

@Service
class CodeWhispererServiceNew(private val cs: CoroutineScope) : Disposable {
    private val codeInsightSettingsFacade = CodeInsightsSettingsFacade()
    private var refreshFailure: Int = 0
    private val ongoingRequests = mutableMapOf<Int, InvocationContextNew?>()
    val ongoingRequestsContext = mutableMapOf<Int, RequestContextNew>()
    private var jobId = 0
    private var sessionContext: SessionContextNew? = null

    init {
        Disposer.register(this, codeInsightSettingsFacade)
    }

    private var job: Job? = null
    fun showRecommendationsInPopup(
        editor: Editor,
        triggerTypeInfo: TriggerTypeInfo,
        latencyContext: LatencyContext,
    ): Job? {
        if (job == null || job?.isCompleted == true) {
            job = cs.launch(getCoroutineBgContext()) {
                doShowRecommendationsInPopup(editor, triggerTypeInfo, latencyContext)
            }
        }

        // did some wrangling, but compiler didn't believe this can't be null
        return job
    }

    private suspend fun doShowRecommendationsInPopup(
        editor: Editor,
        triggerTypeInfo: TriggerTypeInfo,
        latencyContext: LatencyContext,
    ) {
        val project = editor.project ?: return
        if (!isCodeWhispererEnabled(project)) return

        // try to refresh automatically if possible, otherwise ask user to login again
        if (isQExpired(project)) {
            // consider changing to only running once a ~minute since this is relatively expensive
            // say the connection is un-refreshable if refresh fails for 3 times
            val shouldReauth = if (refreshFailure < MAX_REFRESH_ATTEMPT) {
                val attempt = withContext(getCoroutineBgContext()) {
                    promptReAuth(project)
                }

                if (!attempt) {
                    refreshFailure++
                }

                attempt
            } else {
                true
            }

            if (shouldReauth) {
                return
            }
        }

        val psiFile = runReadAction { PsiDocumentManager.getInstance(project).getPsiFile(editor.document) }

        if (psiFile == null) {
            LOG.debug { "No PSI file for the current document" }
            if (triggerTypeInfo.triggerType == CodewhispererTriggerType.OnDemand) {
                showCodeWhispererInfoHint(editor, message("codewhisperer.trigger.document.unsupported"))
            }
            return
        }
        val isInjectedFile = runReadAction { psiFile.isInjectedText() }
        if (isInjectedFile) return

        val currentJobId = jobId++
        val requestContext = try {
            getRequestContext(triggerTypeInfo, editor, project, psiFile)
        } catch (e: Exception) {
            LOG.debug { e.message.toString() }
            return
        }
        val caretContext = requestContext.fileContextInfo.caretContext
        ongoingRequestsContext.forEach { (k, v) ->
            val vCaretContext = v.fileContextInfo.caretContext
            if (vCaretContext == caretContext) {
                LOG.debug { "same caretContext found from job: $k, left context ${vCaretContext.leftContextOnCurrentLine}, jobId: $currentJobId" }
                return
            }
        }

        LOG.debug {
            "Calling CodeWhisperer service, jobId: $currentJobId, trigger type: ${triggerTypeInfo.triggerType}" +
                if (triggerTypeInfo.triggerType == CodewhispererTriggerType.AutoTrigger) {
                    ", auto-trigger type: ${triggerTypeInfo.automatedTriggerType}"
                } else {
                    ""
                }
        }

        CodeWhispererInvocationStatusNew.getInstance().startInvocation()

        invokeCodeWhispererInBackground(requestContext, currentJobId, latencyContext)
    }

    internal suspend fun invokeCodeWhispererInBackground(requestContext: RequestContextNew, currentJobId: Int, latencyContext: LatencyContext) {
        ongoingRequestsContext[currentJobId] = requestContext
        val sessionContext = sessionContext ?: SessionContextNew(requestContext.project, requestContext.editor, latencyContext = latencyContext)

        // In rare cases when there's an ongoing session and subsequent triggers are from a different project or editor --
        // we will cancel the existing session(since we've already moved to a different project or editor simply return.
        if (requestContext.project != sessionContext.project || requestContext.editor != sessionContext.editor) {
            disposeDisplaySession(false)
            return
        }
        this.sessionContext = sessionContext

        val workerContexts = mutableListOf<WorkerContextNew>()

        // When session is disposed we will cancel this coroutine. The only places session can get disposed should be
        // from CodeWhispererService.disposeDisplaySession().
        // It's possible and ok that coroutine will keep running until the next time we check it's state.
        // As long as we don't show to the user extra info we are good.
        var lastRecommendationIndex = -1

        try {
            var startTime = System.nanoTime()
            CodeWhispererInvocationStatusNew.getInstance().setInvocationStart()
            var requestCount = 0
            var nextToken: Either<String, Int>? = null
            do {
                val result = AmazonQLspService.executeIfRunning(requestContext.project) { server ->
                    val params = createInlineCompletionParams(requestContext.editor, requestContext.triggerTypeInfo, nextToken)
                    server.inlineCompletionWithReferences(params)
                }
                result?.thenAccept { completion ->
                    nextToken = completion.partialResultToken
                    requestCount++
                    val endTime = System.nanoTime()
                    val latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime).toDouble()
                    startTime = endTime
                    val responseContext = ResponseContext(completion.sessionId)
                    logServiceInvocation(requestContext, responseContext, completion, latency, null)
                    lastRecommendationIndex += completion.items.size

                    runInEdt {
                        // If delay is not met, add them to the worker queue and process them later.
                        // On first response, workers queue must be empty. If there's enough delay before showing,
                        // process CodeWhisperer UI rendering and workers queue will remain empty throughout this
                        // CodeWhisperer session. If there's not enough delay before showing, the CodeWhisperer UI rendering task
                        // will be added to the workers queue.
                        // On subsequent responses, if they see workers queue is not empty, it means the first worker
                        // task hasn't been finished yet, in this case simply add another task to the queue. If they
                        // see worker queue is empty, the previous tasks must have been finished before this. In this
                        // case render CodeWhisperer UI directly.
                        val workerContext = WorkerContextNew(requestContext, responseContext, completion)
                        if (workerContexts.isNotEmpty()) {
                            workerContexts.add(workerContext)
                        } else {
                            if (ongoingRequests.values.filterNotNull().isEmpty() &&
                                !CodeWhispererInvocationStatusNew.getInstance().hasEnoughDelayToShowCodeWhisperer()
                            ) {
                                // It's the first response, and no enough delay before showing
                                projectCoroutineScope(requestContext.project).launch {
                                    while (!CodeWhispererInvocationStatusNew.getInstance().hasEnoughDelayToShowCodeWhisperer()) {
                                        delay(CodeWhispererConstants.POPUP_DELAY_CHECK_INTERVAL)
                                    }
                                    runInEdt {
                                        workerContexts.forEach {
                                            processCodeWhispererUI(
                                                sessionContext,
                                                it,
                                                ongoingRequests[currentJobId],
                                                cs,
                                                currentJobId
                                            )
                                            if (!ongoingRequests.contains(currentJobId)) {
                                                job?.cancel()
                                            }
                                        }
                                        workerContexts.clear()
                                    }
                                }
                                workerContexts.add(workerContext)
                            } else {
                                // Have enough delay before showing for the first response, or it's subsequent responses
                                processCodeWhispererUI(
                                    sessionContext,
                                    workerContext,
                                    ongoingRequests[currentJobId],
                                    cs,
                                    currentJobId
                                )
                                if (!ongoingRequests.contains(currentJobId)) {
                                    job?.cancel()
                                }
                            }
                        }
                    }
                    if (!cs.isActive) {
                        // If job is cancelled before we do another request, don't bother making
                        // another API call to save resources
                        LOG.debug { "Skipping sending remaining requests on inactive CodeWhisperer session exit" }
                        return@thenAccept
                    }
                    if (requestCount >= PAGINATION_REQUEST_COUNT_ALLOWED) {
                        LOG.debug { "Only $PAGINATION_REQUEST_COUNT_ALLOWED request per pagination session for now" }
                        CodeWhispererInvocationStatusNew.getInstance().finishInvocation()
                        return@thenAccept
                    }
                }
            } while (nextToken != null)
        } catch (e: Exception) {
            val requestId: String
            val sessionId: String
            val displayMessage: String

            if (e is CodeWhispererRuntimeException) {
                requestId = e.requestId().orEmpty()
                sessionId = e.awsErrorDetails().sdkHttpResponse().headers().getOrDefault(KET_SESSION_ID, listOf(requestId))[0]
                displayMessage = e.awsErrorDetails().errorMessage() ?: message("codewhisperer.trigger.error.server_side")
            } else {
                sessionId = ""
                val statusCode = if (e is SdkServiceException) e.statusCode() else 0
                displayMessage =
                    if (statusCode >= 500) {
                        message("codewhisperer.trigger.error.server_side")
                    } else {
                        message("codewhisperer.trigger.error.client_side")
                    }
                if (statusCode < 500) {
                    LOG.debug(e) { "Error invoking CodeWhisperer service" }
                }
            }
            val exceptionType = e::class.simpleName
            val responseContext = ResponseContext(sessionId)
            CodeWhispererInvocationStatusNew.getInstance().setInvocationSessionId(sessionId)
            logServiceInvocation(requestContext, responseContext, null, null, exceptionType)

            if (e is ThrottlingException &&
                e.message == CodeWhispererConstants.THROTTLING_MESSAGE
            ) {
                CodeWhispererExplorerActionManager.getInstance().setSuspended(requestContext.project)
                if (requestContext.triggerTypeInfo.triggerType == CodewhispererTriggerType.OnDemand) {
                    notifyErrorCodeWhispererUsageLimit(requestContext.project)
                }
            } else {
                if (requestContext.triggerTypeInfo.triggerType == CodewhispererTriggerType.OnDemand) {
                    // We should only show error hint when CodeWhisperer popup is not visible,
                    // and make it silent if CodeWhisperer popup is showing.
                    runInEdt {
                        if (!CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive()) {
                            showCodeWhispererErrorHint(requestContext.editor, displayMessage)
                        }
                    }
                }
            }
            CodeWhispererInvocationStatusNew.getInstance().finishInvocation()
            runInEdt {
                CodeWhispererPopupManagerNew.getInstance().updatePopupPanel(sessionContext)
            }
        }
    }

    @RequiresEdt
    private fun processCodeWhispererUI(
        sessionContext: SessionContextNew,
        workerContext: WorkerContextNew,
        currStates: InvocationContextNew?,
        coroutine: CoroutineScope,
        jobId: Int,
    ) {
        val requestContext = workerContext.requestContext
        val responseContext = workerContext.responseContext
        val completions = workerContext.completions

        // At this point when we are in EDT, the state of the popup will be thread-safe
        // across this thread execution, so if popup is disposed, we will stop here.
        // This extra check is needed because there's a time between when we get the response and
        // when we enter the EDT.
        if (!coroutine.isActive || sessionContext.isDisposed()) {
            LOG.debug { "Stop showing CodeWhisperer recommendations on CodeWhisperer session exit. session id: ${completions.sessionId}, jobId: $jobId" }
            return
        }

        if (requestContext.editor.isDisposed) {
            LOG.debug { "Stop showing all CodeWhisperer recommendations since editor is disposed. session id: ${completions.sessionId}, jobId: $jobId" }
            disposeDisplaySession(false)
            return
        }

        CodeWhispererInvocationStatusNew.getInstance().finishInvocation()

        val caretMovement = CodeWhispererEditorManagerNew.getInstance().getCaretMovement(
            requestContext.editor,
            requestContext.caretPosition
        )
        val isPopupShowing = checkRecommendationsValidity(currStates, false)
        val nextStates: InvocationContextNew?
        if (currStates == null) {
            // first response for the jobId
            nextStates = initStates(jobId, requestContext, responseContext, completions, caretMovement)

            // receiving a null state means caret has moved backward,
            // so we are going to cancel the current job
            if (nextStates == null) {
                return
            }
        } else {
            // subsequent responses for the jobId
            nextStates = updateStates(currStates, completions)
        }
        LOG.debug { "Adding ${completions.items.size} completions to the session. session id: ${completions.sessionId}, jobId: $jobId" }

        // TODO: may have bug when it's a mix of auto-trigger + manual trigger
        val hasAtLeastOneValid = checkRecommendationsValidity(nextStates, true)
        val allSuggestions = ongoingRequests.values.filterNotNull().flatMap { it.recommendationContext.details }
        val valid = allSuggestions.count { !it.isDiscarded }
        LOG.debug { "Suggestions status: valid: $valid, discarded: ${allSuggestions.size - valid}" }

        // If there are no recommendations at all in this session, we need to manually send the user decision event here
        // since it won't be sent automatically later
        // TODO: may have bug; visit later
        if (nextStates.recommendationContext.details.isEmpty()) {
            LOG.debug { "Received just an empty list from this session. session id: ${completions.sessionId}" }
        }
        if (!hasAtLeastOneValid) {
            LOG.debug { "None of the recommendations are valid, exiting current CodeWhisperer pagination session" }
            // If there's only one ongoing request, after disposing this, the entire session will also end
            if (ongoingRequests.keys.size == 1) {
                disposeDisplaySession(false)
            } else {
                disposeJob(jobId)
                sessionContext.selectedIndex = CodeWhispererPopupManagerNew.getInstance().findNewSelectedIndex(true, sessionContext.selectedIndex)
            }
        } else {
            updateCodeWhisperer(sessionContext, nextStates, isPopupShowing)
        }
    }

    private fun initStates(
        jobId: Int,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        completions: InlineCompletionListWithReferences,
        caretMovement: CaretMovement,
    ): InvocationContextNew? {
        val visualPosition = requestContext.editor.caretModel.visualPosition

        if (caretMovement == CaretMovement.MOVE_BACKWARD) {
            LOG.debug { "Caret moved backward, discarding all of the recommendations. Session Id: ${completions.sessionId}, jobId: $jobId" }
            val detailContexts = completions.items.map {
                DetailContext("", it, true, getCompletionType(it))
            }.toMutableList()
            val recommendationContext = RecommendationContextNew(detailContexts, "", VisualPosition(0, 0), jobId)
            ongoingRequests[jobId] = buildInvocationContext(requestContext, responseContext, recommendationContext)
            disposeDisplaySession(false)
            return null
        }

        val userInput =
            if (caretMovement == CaretMovement.NO_CHANGE) {
                LOG.debug { "Caret position not changed since invocation. Session Id: ${completions.sessionId}" }
                ""
            } else {
                LOG.debug { "Caret position moved forward since invocation. Session Id: ${completions.sessionId}" }
                CodeWhispererEditorManagerNew.getInstance().getUserInputSinceInvocation(
                    requestContext.editor,
                    requestContext.caretPosition.offset
                )
            }
        val detailContexts = CodeWhispererRecommendationManager.getInstance().buildDetailContext(
            userInput,
            completions,
        )
        val recommendationContext = RecommendationContextNew(detailContexts, userInput, visualPosition, jobId)
        ongoingRequests[jobId] = buildInvocationContext(requestContext, responseContext, recommendationContext)
        return ongoingRequests[jobId]
    }

    private fun updateStates(
        states: InvocationContextNew,
        completions: InlineCompletionListWithReferences,
    ): InvocationContextNew {
        val recommendationContext = states.recommendationContext
        val newDetailContexts = CodeWhispererRecommendationManager.getInstance().buildDetailContext(
            recommendationContext.userInput,
            completions,
        )

        recommendationContext.details.addAll(newDetailContexts)
        return states
    }

    private fun checkRecommendationsValidity(states: InvocationContextNew?, showHint: Boolean): Boolean {
        if (states == null) return false
        val details = states.recommendationContext.details

        // set to true when at least one is not discarded or empty
        val hasAtLeastOneValid = details.any { !it.isDiscarded && it.completion.insertText.isNotEmpty() }

        if (!hasAtLeastOneValid && showHint && states.requestContext.triggerTypeInfo.triggerType == CodewhispererTriggerType.OnDemand) {
            showCodeWhispererInfoHint(
                states.requestContext.editor,
                message("codewhisperer.popup.no_recommendations")
            )
        }
        return hasAtLeastOneValid
    }

    private fun updateCodeWhisperer(sessionContext: SessionContextNew, states: InvocationContextNew, recommendationAdded: Boolean) {
        CodeWhispererPopupManagerNew.getInstance().changeStatesForShowing(sessionContext, states, recommendationAdded)
    }

    @RequiresEdt
    private fun disposeJob(jobId: Int) {
        ongoingRequests[jobId]?.let { Disposer.dispose(it) }
        ongoingRequests.remove(jobId)
        ongoingRequestsContext.remove(jobId)
    }

    @RequiresEdt
    fun disposeDisplaySession(accept: Boolean) {
        // avoid duplicate session disposal logic
        if (sessionContext == null || sessionContext?.isDisposed() == true) return

        sessionContext?.let {
            it.hasAccepted = accept
            Disposer.dispose(it)
        }
        sessionContext = null
        val jobIds = ongoingRequests.keys.toList()
        jobIds.forEach { jobId -> disposeJob(jobId) }
        ongoingRequests.clear()
        ongoingRequestsContext.clear()
    }

    fun getAllSuggestionsPreviewInfo() =
        ongoingRequests.values.filterNotNull().flatMap { element ->
            val context = element.recommendationContext
            context.details.map {
                PreviewContext(context.jobId, it, context.userInput, context.typeahead)
            }
        }

    fun getAllPaginationSessions() = ongoingRequests

    fun getRequestContext(
        triggerTypeInfo: TriggerTypeInfo,
        editor: Editor,
        project: Project,
        psiFile: PsiFile,
    ): RequestContextNew {
        // 1. file context
        val fileContext: FileContextInfo = runReadAction { FileContextProvider.getInstance(project).extractFileContext(editor, psiFile) }

        // 3. caret position
        val caretPosition = runReadAction { getCaretPosition(editor) }

        // 4. connection
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())

        return RequestContextNew(project, editor, triggerTypeInfo, caretPosition, fileContext, connection)
    }

    private fun buildInvocationContext(
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        recommendationContext: RecommendationContextNew,
    ): InvocationContextNew {
        // Creating a disposable for managing all listeners lifecycle attached to the popup.
        // previously(before pagination) we use popup as the parent disposable.
        // After pagination, listeners need to be updated as states are updated, for the same popup,
        // so disposable chain becomes popup -> disposable -> listeners updates, and disposable gets replaced on every
        // state update.
        val states = InvocationContextNew(requestContext, responseContext, recommendationContext)
        Disposer.register(states) {
            job?.cancel(CancellationException("Cancelling the current coroutine when the pagination session context is disposed"))
        }
        return states
    }

    private fun createInlineCompletionParams(
        editor: Editor,
        triggerTypeInfo: TriggerTypeInfo,
        nextToken: Either<String, Int>?,
    ): InlineCompletionWithReferencesParams =
        ReadAction.compute<InlineCompletionWithReferencesParams, RuntimeException> {
            InlineCompletionWithReferencesParams(
                context = InlineCompletionContext(
                    // Map the triggerTypeInfo to appropriate InlineCompletionTriggerKind
                    triggerKind = when (triggerTypeInfo.triggerType) {
                        CodewhispererTriggerType.OnDemand -> InlineCompletionTriggerKind.Invoke
                        CodewhispererTriggerType.AutoTrigger -> InlineCompletionTriggerKind.Automatic
                        else -> InlineCompletionTriggerKind.Invoke
                    }
                ),
            ).apply {
                textDocument = TextDocumentIdentifier(toUriString(editor.virtualFile))
                position = Position(
                    editor.caretModel.primaryCaret.logicalPosition.line,
                    editor.caretModel.primaryCaret.logicalPosition.column
                )
                if (nextToken != null) {
                    workDoneToken = nextToken
                }
            }
        }

    private fun logServiceInvocation(
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        completions: InlineCompletionListWithReferences?,
        latency: Double?,
        exceptionType: String?,
    ) {
        val recommendationLogs = completions?.items?.map { it.insertText.trimEnd() }
            ?.reduceIndexedOrNull { index, acc, recommendation -> "$acc\n[${index + 1}]\n$recommendation" }
        LOG.info {
            "SessionId: ${responseContext.sessionId}, " +
                "Jetbrains IDE: ${ApplicationInfo.getInstance().fullApplicationName}, " +
                "IDE version: ${ApplicationInfo.getInstance().apiVersion}, " +
                "Filename: ${requestContext.fileContextInfo.filename}, " +
                "Left context of current line: ${requestContext.fileContextInfo.caretContext.leftContextOnCurrentLine}, " +
                "Cursor line: ${requestContext.caretPosition.line}, " +
                "Caret offset: ${requestContext.caretPosition.offset}, " +
                latency?.let { "Latency: $latency, " }.orEmpty() +
                exceptionType?.let { "Exception Type: $it, " }.orEmpty() +
                "Recommendations: \n${recommendationLogs ?: "None"}"
        }
    }

    fun canDoInvocation(editor: Editor, type: CodewhispererTriggerType): Boolean {
        editor.project?.let {
            if (!isCodeWhispererEnabled(it)) {
                return false
            }
        }

        if (type == CodewhispererTriggerType.AutoTrigger && !CodeWhispererExplorerActionManager.getInstance().isAutoEnabled()) {
            LOG.debug { "CodeWhisperer auto-trigger is disabled, not invoking service" }
            return false
        }

        if (CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive()) {
            LOG.debug { "Find an existing CodeWhisperer session before triggering CodeWhisperer, not invoking service" }
            return false
        }
        return true
    }

    fun showCodeWhispererInfoHint(editor: Editor, message: String) {
        HintManager.getInstance().showInformationHint(editor, message, HintManager.UNDER)
    }

    fun showCodeWhispererErrorHint(editor: Editor, message: String) {
        HintManager.getInstance().showErrorHint(editor, message, HintManager.UNDER)
    }

    override fun dispose() {}

    companion object {
        private val LOG = getLogger<CodeWhispererServiceNew>()
        private const val MAX_REFRESH_ATTEMPT = 3
        private const val PAGINATION_REQUEST_COUNT_ALLOWED = 1

        val CODEWHISPERER_INTELLISENSE_POPUP_ON_HOVER: Topic<CodeWhispererIntelliSenseOnHoverListener> = Topic.create(
            "CodeWhisperer intelliSense popup on hover",
            CodeWhispererIntelliSenseOnHoverListener::class.java
        )
        val KEY_SESSION_CONTEXT = Key.create<SessionContextNew>("codewhisperer.session")

        fun getInstance(): CodeWhispererServiceNew = service()
        const val KET_SESSION_ID = "x-amzn-SessionId"
    }
}

data class RequestContextNew(
    val project: Project,
    val editor: Editor,
    val triggerTypeInfo: TriggerTypeInfo,
    val caretPosition: CaretPosition,
    val fileContextInfo: FileContextInfo,
    val connection: ToolkitConnection?,
)

interface CodeWhispererIntelliSenseOnHoverListener {
    fun onEnter() {}
}
