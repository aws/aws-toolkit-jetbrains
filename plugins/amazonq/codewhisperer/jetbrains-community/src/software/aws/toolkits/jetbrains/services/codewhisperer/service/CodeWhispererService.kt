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
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.JsonRpcException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import software.amazon.awssdk.core.exception.SdkServiceException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.coroutines.EDT
import software.amazon.q.jetbrains.core.coroutines.getCoroutineBgContext
import software.amazon.q.jetbrains.core.credentials.ToolkitConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.GetConfigurationFromServerParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LspServerConfigurations
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionContext
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionTriggerKind
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionWithReferencesParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil.toUriString
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManager
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.getCaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.WorkerContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CaretMovement
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeInsightsSettingsFacade
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCompletionType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.notifyErrorCodeWhispererUsageLimit
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.amazon.q.jetbrains.utils.isInjectedText
import software.amazon.q.jetbrains.utils.isQExpired
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class CodeWhispererService(private val cs: CoroutineScope) : Disposable {
    private val codeInsightSettingsFacade = CodeInsightsSettingsFacade()
    private var refreshFailure: Int = 0

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

        val requestContext = try {
            getRequestContext(triggerTypeInfo, editor, project, psiFile, latencyContext)
        } catch (e: Exception) {
            LOG.debug { e.message.toString() }
            return
        }

        // TODO flare: since IDE local language check got removed, flare needs to implement json aws template support only

        LOG.debug {
            "Calling CodeWhisperer service, trigger type: ${triggerTypeInfo.triggerType}" +
                if (triggerTypeInfo.triggerType == CodewhispererTriggerType.AutoTrigger) {
                    ", auto-trigger type: ${triggerTypeInfo.automatedTriggerType}"
                } else {
                    ""
                }
        }

        val invocationStatus = CodeWhispererInvocationStatus.getInstance()
        if (invocationStatus.checkExistingInvocationAndSet()) {
            return
        }

        invokeCodeWhispererInBackground(requestContext)
    }

    internal suspend fun invokeCodeWhispererInBackground(requestContext: RequestContext): Job {
        val popup = withContext(EDT) {
            CodeWhispererPopupManager.getInstance().initPopup().also {
                Disposer.register(it) { CodeWhispererInvocationStatus.getInstance().finishInvocation() }
            }
        }

        var states: InvocationContext? = null

        val job = cs.launch {
            try {
                var startTime = System.nanoTime()
                CodeWhispererInvocationStatus.getInstance().setInvocationStart()
                var nextToken: Either<String, Int>? = null
                do {
                    val result = AmazonQLspService.executeAsyncIfRunning(requestContext.project) { server ->
                        val params = createInlineCompletionParams(requestContext.editor, requestContext.triggerTypeInfo, nextToken)
                        server.inlineCompletionWithReferences(params)
                    }
                    val completion = result?.await()
                    if (completion == null) {
                        // no result / not running
                        CodeWhispererInvocationStatus.getInstance().finishInvocation()
                        break
                    }

                    nextToken = completion.partialResultToken
                    val endTime = System.nanoTime()
                    val latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime).toDouble()
                    startTime = endTime
                    val responseContext = ResponseContext(completion.sessionId)
                    logServiceInvocation(requestContext, responseContext, completion, latency, null)

                    val workerContext = WorkerContext(requestContext, responseContext, completion, popup)
                    runInEdt {
                        states = processCodeWhispererUI(workerContext, states)
                    }
                    if (popup.isDisposed) {
                        LOG.debug { "Skipping sending remaining requests on CodeWhisperer session exit" }
                        return@launch
                    }
                } while (nextToken != null && nextToken.left.isNotEmpty())
            } catch (e: Exception) {
                // TODO flare: flare doesn't return exceptions
                val sessionId = ""
                val displayMessage: String

                if (e is JsonRpcException) {
                    // TODO: only log once to avoid auto-trigger spam?
                    LOG.debug(e) {
                        "Error talking to Q LSP server"
                    }
                    displayMessage = "Q LSP server failed to communicate, try restarting the current project."
                } else {
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
                CodeWhispererInvocationStatus.getInstance().setInvocationSessionId(sessionId)
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
                        if (!CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive()) {
                            showCodeWhispererErrorHint(requestContext.editor, displayMessage)
                        }
                    }
                }
                CodeWhispererInvocationStatus.getInstance().finishInvocation()
                runInEdt {
                    states?.let {
                        CodeWhispererPopupManager.getInstance().updatePopupPanel(
                            it,
                            CodeWhispererPopupManager.getInstance().sessionContext
                        )
                    }
                }
            } finally {
                CodeWhispererInvocationStatus.getInstance().setInvocationComplete()
            }
        }

        return job
    }

    @RequiresEdt
    private fun processCodeWhispererUI(workerContext: WorkerContext, currStates: InvocationContext?): InvocationContext? {
        val requestContext = workerContext.requestContext
        val responseContext = workerContext.responseContext
        val completions = workerContext.completions
        val popup = workerContext.popup

        // At this point when we are in EDT, the state of the popup will be thread-safe
        // across this thread execution, so if popup is disposed, we will stop here.
        // This extra check is needed because there's a time between when we get the response and
        // when we enter the EDT.
        if (popup.isDisposed) {
            LOG.debug { "Stop showing CodeWhisperer recommendations on CodeWhisperer session exit. session id: ${responseContext.sessionId}" }
            return null
        }

        if (requestContext.editor.isDisposed) {
            LOG.debug { "Stop showing CodeWhisperer recommendations since editor is disposed. session id: ${responseContext.sessionId}" }
            sendDiscardedUserDecisionEventForAll(requestContext.project, requestContext.latencyContext, responseContext.sessionId, completions)
            CodeWhispererPopupManager.getInstance().cancelPopup(popup)
            return null
        }

        if (completions.partialResultToken?.left.isNullOrEmpty()) {
            CodeWhispererInvocationStatus.getInstance().finishInvocation()
        }

        val caretMovement = CodeWhispererEditorManager.getInstance().getCaretMovement(
            requestContext.editor,
            requestContext.caretPosition
        )
        val isPopupShowing: Boolean
        val nextStates: InvocationContext?
        if (currStates == null) {
            // first response
            nextStates = initStates(requestContext, responseContext, completions, caretMovement, popup)
            isPopupShowing = false

            // receiving a null state means caret has moved backward or there's a conflict with
            // Intellisense popup, so we are going to cancel the job
            if (nextStates == null) {
                LOG.debug { "Cancelling popup and exiting CodeWhisperer session. session id: ${responseContext.sessionId}" }
                sendDiscardedUserDecisionEventForAll(requestContext.project, requestContext.latencyContext, responseContext.sessionId, completions)
                CodeWhispererPopupManager.getInstance().cancelPopup(popup)
                return null
            }
        } else {
            // subsequent responses
            nextStates = updateStates(currStates, completions)
            isPopupShowing = checkRecommendationsValidity(currStates, false)
        }

        val hasAtLeastOneValid = checkRecommendationsValidity(nextStates, completions.partialResultToken == null)

        // If there are no recommendations at all in this session, we need to manually send the user decision event here
        // since it won't be sent automatically later
        if (!hasAtLeastOneValid) {
            if (completions.partialResultToken?.left.isNullOrEmpty()) {
                LOG.debug { "None of the recommendations are valid, exiting CodeWhisperer session" }
                CodeWhispererPopupManager.getInstance().cancelPopup(popup)
                return null
            }
        } else {
            updateCodeWhisperer(nextStates, isPopupShowing)
        }
        return nextStates
    }

    private fun initStates(
        requestContext: RequestContext,
        responseContext: ResponseContext,
        completions: InlineCompletionListWithReferences,
        caretMovement: CaretMovement,
        popup: JBPopup,
    ): InvocationContext? {
        val visualPosition = requestContext.editor.caretModel.visualPosition

        if (CodeWhispererPopupManager.getInstance().hasConflictingPopups(requestContext.editor)) {
            LOG.debug { "Detect conflicting popup window with CodeWhisperer popup, not showing CodeWhisperer popup" }
            return null
        }

        if (caretMovement == CaretMovement.MOVE_BACKWARD) {
            LOG.debug { "Caret moved backward, discarding all of the recommendations. Session Id: ${completions.sessionId}" }
            return null
        }
        val userInput =
            if (caretMovement == CaretMovement.NO_CHANGE) {
                LOG.debug { "Caret position not changed since invocation. Session Id: ${completions.sessionId}" }
                ""
            } else {
                LOG.debug { "Caret position moved forward since invocation. Session Id: ${completions.sessionId}" }
                CodeWhispererEditorManager.getInstance().getUserInputSinceInvocation(
                    requestContext.editor,
                    requestContext.caretPosition.offset
                )
            }
        val detailContexts = CodeWhispererRecommendationManager.getInstance().buildDetailContext(
            userInput,
            completions,
        )
        val recommendationContext = RecommendationContext(detailContexts, userInput, visualPosition)
        return buildInvocationContext(requestContext, responseContext, recommendationContext, popup)
    }

    private fun updateStates(
        states: InvocationContext,
        completions: InlineCompletionListWithReferences,
    ): InvocationContext {
        val recommendationContext = states.recommendationContext
        val details = recommendationContext.details
        val newDetailContexts = CodeWhispererRecommendationManager.getInstance().buildDetailContext(
            recommendationContext.userInput,
            completions,
        )
        Disposer.dispose(states)

        val updatedStates = states.copy(
            recommendationContext = recommendationContext.copy(details = details + newDetailContexts)
        )
        Disposer.register(states.popup, updatedStates)
        CodeWhispererPopupManager.getInstance().initPopupListener(updatedStates)
        return updatedStates
    }

    private fun checkRecommendationsValidity(states: InvocationContext, showHint: Boolean): Boolean {
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

    private fun updateCodeWhisperer(states: InvocationContext, recommendationAdded: Boolean) {
        CodeWhispererPopupManager.getInstance().changeStates(states, 0, recommendationAdded)
    }

    private fun sendDiscardedUserDecisionEventForAll(
        project: Project,
        latencyContext: LatencyContext,
        sessionId: String,
        completions: InlineCompletionListWithReferences,
    ) {
        val detailContexts = completions.items.map {
            DetailContext(it.itemId, it, true, getCompletionType(it))
        }
        val recommendationContext = RecommendationContext(detailContexts, "", VisualPosition(0, 0))
        CodeWhispererTelemetryService.getInstance().sendUserTriggerDecisionEvent(project, latencyContext, sessionId, recommendationContext)
    }

    suspend fun getRequestContext(
        triggerTypeInfo: TriggerTypeInfo,
        editor: Editor,
        project: Project,
        psiFile: PsiFile,
        latencyContext: LatencyContext,
    ): RequestContext {
        // 1. file context
        val fileContext: FileContextInfo = runReadAction { FileContextProvider.getInstance(project).extractFileContext(editor, psiFile) }

        // 3. caret position
        val caretPosition = runReadAction { getCaretPosition(editor) }

        // 4. connection
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())

        // 5. customization
        val customizationArn = CodeWhispererModelConfigurator.getInstance().activeCustomization(project)?.arn

        var workspaceId: String? = null
        try {
            val workspacesInfos = getWorkspaceIds(project).get().workspaces
            for (workspaceInfo in workspacesInfos) {
                val workspaceRootPath = Paths.get(URI(workspaceInfo.workspaceRoot)).toString()
                if (psiFile.virtualFile.path.startsWith(workspaceRootPath)) {
                    workspaceId = workspaceInfo.workspaceId
                    LOG.info { "Found workspaceId from LSP '$workspaceId'" }
                    break
                }
            }
        } catch (e: Exception) {
            LOG.warn { "Cannot get workspaceId from LSP'$e'" }
        }
        return RequestContext(
            project,
            editor,
            triggerTypeInfo,
            caretPosition,
            fileContext,
            connection,
            latencyContext,
            customizationArn,
            workspaceId,
        )
    }

    suspend fun getWorkspaceIds(project: Project): CompletableFuture<LspServerConfigurations> {
        val payload = GetConfigurationFromServerParams(
            section = "aws.q.workspaceContext"
        )
        return AmazonQLspService.executeAsyncIfRunning(project) { server ->
            server.getConfigurationFromServer(payload)
        } ?: (CompletableFuture.failedFuture(IllegalStateException("LSP Server not running")))
    }

    private fun buildInvocationContext(
        requestContext: RequestContext,
        responseContext: ResponseContext,
        recommendationContext: RecommendationContext,
        popup: JBPopup,
    ): InvocationContext {
        addPopupChildDisposables(popup)
        // Creating a disposable for managing all listeners lifecycle attached to the popup.
        // previously(before pagination) we use popup as the parent disposable.
        // After pagination, listeners need to be updated as states are updated, for the same popup,
        // so disposable chain becomes popup -> disposable -> listeners updates, and disposable gets replaced on every
        // state update.
        val states = InvocationContext(requestContext, responseContext, recommendationContext, popup)
        Disposer.register(popup, states)
        CodeWhispererPopupManager.getInstance().initPopupListener(states)
        return states
    }

    fun createInlineCompletionParams(
        editor: Editor,
        triggerTypeInfo: TriggerTypeInfo,
        nextToken: Either<String, Int>?,
    ): InlineCompletionWithReferencesParams {
        // Resolve and validate the virtualFile before entering the ReadAction
        val virtualFile = editor.virtualFile
            ?: error("Editor virtualFile is null for CodeWhisperer inline completion")

        return ReadAction.compute<InlineCompletionWithReferencesParams, RuntimeException> {
            InlineCompletionWithReferencesParams(
                context = InlineCompletionContext(
                    // Map the triggerTypeInfo to appropriate InlineCompletionTriggerKind
                    triggerKind = when (triggerTypeInfo.triggerType) {
                        CodewhispererTriggerType.OnDemand -> InlineCompletionTriggerKind.Invoke
                        CodewhispererTriggerType.AutoTrigger -> InlineCompletionTriggerKind.Automatic
                        else -> InlineCompletionTriggerKind.Invoke
                    }
                ),
                documentChangeParams =
                if (triggerTypeInfo.automatedTriggerType == CodeWhispererAutomatedTriggerType.IntelliSense()) {
                    DidChangeTextDocumentParams(
                        VersionedTextDocumentIdentifier(),
                        listOf(
                            TextDocumentContentChangeEvent(
                                null,
                                CodeWhispererAutomatedTriggerType.IntelliSense().toString()
                            )
                        ),
                    )
                } else {
                    null
                },
                openTabFilepaths = editor.project?.let { project ->
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                        .openFiles.mapNotNull { toUriString(it) }
                }.orEmpty(),
            ).apply {
                textDocument = TextDocumentIdentifier(toUriString(virtualFile))
                position = Position(
                    editor.caretModel.primaryCaret.logicalPosition.line,
                    editor.caretModel.primaryCaret.logicalPosition.column
                )
                if (nextToken != null) {
                    partialResultToken = nextToken
                }
            }
        }
    }

    private fun addPopupChildDisposables(popup: JBPopup) {
        codeInsightSettingsFacade.disableCodeInsightUntil(popup)

        Disposer.register(popup) {
            CodeWhispererPopupManager.getInstance().reset()
        }
    }

    private fun logServiceInvocation(
        requestContext: RequestContext,
        responseContext: ResponseContext,
        completion: InlineCompletionListWithReferences?,
        latency: Double?,
        exceptionType: String?,
    ) {
        val recommendationLogs = completion?.items?.map { it.insertText.trimEnd() }
            ?.reduceIndexedOrNull { index, acc, recommendation -> "$acc\n[${index + 1}]\n$recommendation" }
        LOG.info {
            "SessionId: ${responseContext.sessionId}, " +
                "Jetbrains IDE: ${ApplicationInfo.getInstance().fullApplicationName}, " +
                "IDE version: ${ApplicationInfo.getInstance().apiVersion}, " +
                "Filename: ${requestContext.fileContextInfo.filename}, " +
                "Left context of current line: ${requestContext.fileContextInfo.caretContext.leftContextOnCurrentLine}, " +
                "Cursor line: ${requestContext.caretPosition.line}, " +
                "Caret offset: ${requestContext.caretPosition.offset}, " +
                (latency?.let { "Latency: $latency, " } ?: "") +
                (exceptionType?.let { "Exception Type: $it, " } ?: "") +
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

        if (CodeWhispererPopupManager.getInstance().hasConflictingPopups(editor)) {
            LOG.debug { "Find other active popup windows before triggering CodeWhisperer, not invoking service" }
            return false
        }

        if (CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive()) {
            LOG.debug { "Find an existing CodeWhisperer popup window before triggering CodeWhisperer, not invoking service" }
            return false
        }
        return true
    }

    private fun showCodeWhispererInfoHint(editor: Editor, message: String) {
        runInEdt {
            HintManager.getInstance().showInformationHint(editor, message, HintManager.UNDER)
        }
    }

    private fun showCodeWhispererErrorHint(editor: Editor, message: String) {
        runInEdt {
            HintManager.getInstance().showErrorHint(editor, message, HintManager.UNDER)
        }
    }

    override fun dispose() {}

    companion object {
        private val LOG = getLogger<CodeWhispererService>()
        private const val MAX_REFRESH_ATTEMPT = 3

        fun getInstance(): CodeWhispererService = service()
        const val KET_SESSION_ID = "x-amzn-SessionId"
        private var reAuthPromptShown = false

        fun markReAuthPromptShown() {
            reAuthPromptShown = true
        }

        fun hasReAuthPromptBeenShown() = reAuthPromptShown
    }
}

data class RequestContext(
    val project: Project,
    val editor: Editor,
    val triggerTypeInfo: TriggerTypeInfo,
    val caretPosition: CaretPosition,
    val fileContextInfo: FileContextInfo,
    val connection: ToolkitConnection?,
    val latencyContext: LatencyContext,
    val customizationArn: String?,
    val workspaceId: String?,
)

data class ResponseContext(
    val sessionId: String,
)
