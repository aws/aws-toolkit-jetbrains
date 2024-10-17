// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.codeInsight.hint.HintManager
import com.intellij.notification.NotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.exception.SdkServiceException
import software.amazon.awssdk.core.util.DefaultSdkAutoConstructList
import software.amazon.awssdk.services.codewhisperer.model.CodeWhispererException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.Completion
import software.amazon.awssdk.services.codewhispererruntime.model.FileContext
import software.amazon.awssdk.services.codewhispererruntime.model.GenerateCompletionsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GenerateCompletionsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ProgrammingLanguage
import software.amazon.awssdk.services.codewhispererruntime.model.RecommendationsWithReferencesPreference
import software.amazon.awssdk.services.codewhispererruntime.model.ResourceNotFoundException
import software.amazon.awssdk.services.codewhispererruntime.model.SupplementalContext
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineBgContext
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.getCaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.isSupportedJsonFormat
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isCodeWhispererEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJson
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CaretPosition
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SupplementalContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.TriggerTypeInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.WorkerContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService.Companion.CODEWHISPERER_CODE_COMPLETION_PERFORMED
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CaretMovement
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeInsightsSettingsFacade
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.SUPPLEMENTAL_CONTEXT_TIMEOUT
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCompletionType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getTelemetryOptOutPreference
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.notifyErrorCodeWhispererUsageLimit
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.FileContextProvider
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.utils.isInjectedText
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.jetbrains.utils.notifyWarn
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
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

        latencyContext.credentialFetchingStart = System.nanoTime()

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

        latencyContext.credentialFetchingEnd = System.nanoTime()
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
            CodeWhispererTelemetryServiceNew.getInstance().sendFailedServiceInvocationEvent(project, e::class.simpleName)
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

        val language = requestContext.fileContextInfo.programmingLanguage
        val leftContext = requestContext.fileContextInfo.caretContext.leftFileContext
        if (!language.isCodeCompletionSupported() || (
                language is CodeWhispererJson && !isSupportedJsonFormat(
                    requestContext.fileContextInfo.filename,
                    leftContext
                )
                )
        ) {
            LOG.debug { "Programming language $language is not supported by CodeWhisperer" }
            if (triggerTypeInfo.triggerType == CodewhispererTriggerType.OnDemand) {
                showCodeWhispererInfoHint(
                    requestContext.editor,
                    message("codewhisperer.language.error", psiFile.fileType.name)
                )
            }
            return
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
            val responseIterable = CodeWhispererClientAdaptor.getInstance(requestContext.project).generateCompletionsPaginator(
                buildCodeWhispererRequest(
                    requestContext.fileContextInfo,
                    requestContext.awaitSupplementalContext(),
                    requestContext.customizationArn
                )
            )

            var startTime = System.nanoTime()
            latencyContext.codewhispererPreprocessingEnd = System.nanoTime()
            latencyContext.paginationAllCompletionsStart = System.nanoTime()
            CodeWhispererInvocationStatusNew.getInstance().setInvocationStart()
            var requestCount = 0
            for (response in responseIterable) {
                requestCount++
                val endTime = System.nanoTime()
                val latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime).toDouble()
                startTime = endTime
                val requestId = response.responseMetadata().requestId()
                val sessionId = response.sdkHttpResponse().headers().getOrDefault(KET_SESSION_ID, listOf(requestId))[0]
                if (requestCount == 1) {
                    latencyContext.codewhispererPostprocessingStart = System.nanoTime()
                    latencyContext.paginationFirstCompletionTime = latency
                    latencyContext.firstRequestId = requestId
                    CodeWhispererInvocationStatusNew.getInstance().setInvocationSessionId(sessionId)
                }
                if (response.nextToken().isEmpty()) {
                    latencyContext.paginationAllCompletionsEnd = System.nanoTime()
                }
                val responseContext = ResponseContext(sessionId)
                logServiceInvocation(requestId, requestContext, responseContext, response.completions(), latency, null)
                lastRecommendationIndex += response.completions().size
                ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_CODE_COMPLETION_PERFORMED)
                    .onSuccess(requestContext.fileContextInfo)
                CodeWhispererTelemetryServiceNew.getInstance().sendServiceInvocationEvent(
                    currentJobId,
                    requestId,
                    requestContext,
                    responseContext,
                    lastRecommendationIndex,
                    true,
                    latency,
                    null
                )

                val validatedResponse = validateResponse(response)

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
                    val workerContext = WorkerContextNew(requestContext, responseContext, validatedResponse)
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
                    return
                }
                if (requestCount >= PAGINATION_REQUEST_COUNT_ALLOWED) {
                    LOG.debug { "Only $PAGINATION_REQUEST_COUNT_ALLOWED request per pagination session for now" }
                    CodeWhispererInvocationStatusNew.getInstance().finishInvocation()
                    break
                }
            }
        } catch (e: Exception) {
            val requestId: String
            val sessionId: String
            val displayMessage: String

            if (
                CodeWhispererConstants.Customization.invalidCustomizationExceptionPredicate(e) ||
                e is ResourceNotFoundException
            ) {
                (e as CodeWhispererRuntimeException)

                requestId = e.requestId() ?: ""
                sessionId = e.awsErrorDetails().sdkHttpResponse().headers().getOrDefault(KET_SESSION_ID, listOf(requestId))[0]
                val exceptionType = e::class.simpleName
                val responseContext = ResponseContext(sessionId)

                CodeWhispererTelemetryServiceNew.getInstance().sendServiceInvocationEvent(
                    currentJobId,
                    requestId,
                    requestContext,
                    responseContext,
                    lastRecommendationIndex,
                    false,
                    0.0,
                    exceptionType
                )

                LOG.debug {
                    "The provided customization ${requestContext.customizationArn} is not found, " +
                        "will fallback to the default and retry generate completion"
                }
                logServiceInvocation(requestId, requestContext, responseContext, emptyList(), null, exceptionType)

                notifyWarn(
                    title = "",
                    content = message("codewhisperer.notification.custom.not_available"),
                    project = requestContext.project,
                    notificationActions = listOf(
                        NotificationAction.create(
                            message("codewhisperer.notification.custom.simple.button.select_another_customization")
                        ) { _, notification ->
                            CodeWhispererModelConfigurator.getInstance().showConfigDialog(requestContext.project)
                            notification.expire()
                        }
                    )
                )
                CodeWhispererInvocationStatusNew.getInstance().finishInvocation()

                requestContext.customizationArn?.let { CodeWhispererModelConfigurator.getInstance().invalidateCustomization(it) }

                showRecommendationsInPopup(
                    requestContext.editor,
                    requestContext.triggerTypeInfo,
                    latencyContext
                )
                return
            } else if (e is CodeWhispererException) {
                requestId = e.requestId() ?: ""
                sessionId = e.awsErrorDetails().sdkHttpResponse().headers().getOrDefault(KET_SESSION_ID, listOf(requestId))[0]
                displayMessage = e.awsErrorDetails().errorMessage() ?: message("codewhisperer.trigger.error.server_side")
            } else if (e is CodeWhispererRuntimeException) {
                requestId = e.requestId() ?: ""
                sessionId = e.awsErrorDetails().sdkHttpResponse().headers().getOrDefault(KET_SESSION_ID, listOf(requestId))[0]
                displayMessage = e.awsErrorDetails().errorMessage() ?: message("codewhisperer.trigger.error.server_side")
            } else {
                requestId = ""
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
            logServiceInvocation(requestId, requestContext, responseContext, emptyList(), null, exceptionType)
            CodeWhispererTelemetryServiceNew.getInstance().sendServiceInvocationEvent(
                currentJobId,
                requestId,
                requestContext,
                responseContext,
                lastRecommendationIndex,
                false,
                0.0,
                exceptionType
            )

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
        val response = workerContext.response
        val requestId = response.responseMetadata().requestId()

        // At this point when we are in EDT, the state of the popup will be thread-safe
        // across this thread execution, so if popup is disposed, we will stop here.
        // This extra check is needed because there's a time between when we get the response and
        // when we enter the EDT.
        if (!coroutine.isActive || sessionContext.isDisposed()) {
            LOG.debug { "Stop showing CodeWhisperer recommendations on CodeWhisperer session exit. RequestId: $requestId, jobId: $jobId" }
            return
        }

        if (requestContext.editor.isDisposed) {
            LOG.debug { "Stop showing all CodeWhisperer recommendations since editor is disposed. RequestId: $requestId, jobId: $jobId" }
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
            nextStates = initStates(jobId, requestContext, responseContext, response, caretMovement)

            // receiving a null state means caret has moved backward,
            // so we are going to cancel the current job
            if (nextStates == null) {
                return
            }
        } else {
            // subsequent responses for the jobId
            nextStates = updateStates(currStates, response)
        }
        LOG.debug { "Adding ${response.completions().size} completions to the session. RequestId: $requestId, jobId: $jobId" }

        // TODO: may have bug when it's a mix of auto-trigger + manual trigger
        val hasAtLeastOneValid = checkRecommendationsValidity(nextStates, true)
        val allSuggestions = ongoingRequests.values.filterNotNull().flatMap { it.recommendationContext.details }
        val valid = allSuggestions.filter { !it.isDiscarded }.size
        LOG.debug { "Suggestions status: valid: $valid, discarded: ${allSuggestions.size - valid}" }

        // If there are no recommendations at all in this session, we need to manually send the user decision event here
        // since it won't be sent automatically later
        // TODO: may have bug; visit later
        if (nextStates.recommendationContext.details.isEmpty()) {
            LOG.debug { "Received just an empty list from this session, requestId: $requestId" }
            CodeWhispererTelemetryServiceNew.getInstance().sendUserDecisionEvent(
                requestContext,
                responseContext,
                DetailContextNew(
                    requestId,
                    Completion.builder().build(),
                    Completion.builder().build(),
                    false,
                    false,
                    "",
                    CodewhispererCompletionType.Line
                ),
                -1,
                CodewhispererSuggestionState.Empty,
                nextStates.recommendationContext.details.size
            )
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
        response: GenerateCompletionsResponse,
        caretMovement: CaretMovement,
    ): InvocationContextNew? {
        val requestId = response.responseMetadata().requestId()
        val recommendations = response.completions()
        val visualPosition = requestContext.editor.caretModel.visualPosition

        if (caretMovement == CaretMovement.MOVE_BACKWARD) {
            LOG.debug { "Caret moved backward, discarding all of the recommendations and exiting the session. Request ID: $requestId, jobId: $jobId" }
            val detailContexts = recommendations.map {
                DetailContextNew("", it, it, true, false, "", getCompletionType(it))
            }.toMutableList()
            val recommendationContext = RecommendationContextNew(detailContexts, "", "", VisualPosition(0, 0), jobId)
            ongoingRequests[jobId] = buildInvocationContext(requestContext, responseContext, recommendationContext)
            disposeDisplaySession(false)
            return null
        }

        val userInputOriginal = CodeWhispererEditorManagerNew.getInstance().getUserInputSinceInvocation(
            requestContext.editor,
            requestContext.caretPosition.offset
        )
        val userInput =
            if (caretMovement == CaretMovement.NO_CHANGE) {
                LOG.debug { "Caret position not changed since invocation. Request ID: $requestId" }
                ""
            } else {
                userInputOriginal.trimStart().also {
                    LOG.debug {
                        "Caret position moved forward since invocation. Request ID: $requestId, " +
                            "user input since invocation: $userInputOriginal, " +
                            "user input without leading spaces: $it"
                    }
                }
            }
        val detailContexts = CodeWhispererRecommendationManager.getInstance().buildDetailContext(
            requestContext,
            userInput,
            recommendations,
            requestId
        )
        val recommendationContext = RecommendationContextNew(detailContexts, userInputOriginal, userInput, visualPosition, jobId)
        ongoingRequests[jobId] = buildInvocationContext(requestContext, responseContext, recommendationContext)
        return ongoingRequests[jobId]
    }

    private fun updateStates(
        states: InvocationContextNew,
        response: GenerateCompletionsResponse,
    ): InvocationContextNew {
        val recommendationContext = states.recommendationContext
        val newDetailContexts = CodeWhispererRecommendationManager.getInstance().buildDetailContext(
            states.requestContext,
            recommendationContext.userInputSinceInvocation,
            response.completions(),
            response.responseMetadata().requestId()
        )

        recommendationContext.details.addAll(newDetailContexts)
        return states
    }

    private fun checkRecommendationsValidity(states: InvocationContextNew?, showHint: Boolean): Boolean {
        if (states == null) return false
        val details = states.recommendationContext.details

        // set to true when at least one is not discarded or empty
        val hasAtLeastOneValid = details.any { !it.isDiscarded && it.recommendation.content().isNotEmpty() }

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
                PreviewContext(context.jobId, it, context.userInputSinceInvocation, context.typeahead)
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

        // the upper bound for supplemental context duration is 50ms
        // 2. supplemental context
        val supplementalContext = cs.async {
            try {
                FileContextProvider.getInstance(project).extractSupplementalFileContext(psiFile, fileContext, timeout = SUPPLEMENTAL_CONTEXT_TIMEOUT)
            } catch (e: Exception) {
                LOG.warn { "Run into unexpected error when fetching supplemental context, error: ${e.message}" }
                null
            }
        }

        // 3. caret position
        val caretPosition = runReadAction { getCaretPosition(editor) }

        // 4. connection
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())

        // 5. customization
        val customizationArn = CodeWhispererModelConfigurator.getInstance().activeCustomization(project)?.arn

        return RequestContextNew(project, editor, triggerTypeInfo, caretPosition, fileContext, supplementalContext, connection, customizationArn)
    }

    fun validateResponse(response: GenerateCompletionsResponse): GenerateCompletionsResponse {
        // If contentSpans in reference are not consistent with content(recommendations),
        // remove the incorrect references.
        val validatedRecommendations = response.completions().map {
            val validReferences = it.hasReferences() && it.references().isNotEmpty() &&
                it.references().none { reference ->
                    val span = reference.recommendationContentSpan()
                    span.start() > span.end() || span.start() < 0 || span.end() > it.content().length
                }
            if (validReferences) {
                it
            } else {
                it.toBuilder().references(DefaultSdkAutoConstructList.getInstance()).build()
            }
        }

        return response.toBuilder().completions(validatedRecommendations).build()
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

    private fun logServiceInvocation(
        requestId: String,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        recommendations: List<Completion>,
        latency: Double?,
        exceptionType: String?,
    ) {
        val recommendationLogs = recommendations.map { it.content().trimEnd() }
            .reduceIndexedOrNull { index, acc, recommendation -> "$acc\n[${index + 1}]\n$recommendation" }
        LOG.info {
            "SessionId: ${responseContext.sessionId}, " +
                "RequestId: $requestId, " +
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
        private var reAuthPromptShown = false

        fun markReAuthPromptShown() {
            reAuthPromptShown = true
        }

        fun hasReAuthPromptBeenShown() = reAuthPromptShown

        fun buildCodeWhispererRequest(
            fileContextInfo: FileContextInfo,
            supplementalContext: SupplementalContextInfo?,
            customizationArn: String?,
        ): GenerateCompletionsRequest {
            val programmingLanguage = ProgrammingLanguage.builder()
                .languageName(fileContextInfo.programmingLanguage.toCodeWhispererRuntimeLanguage().languageId)
                .build()
            val fileContext = FileContext.builder()
                .leftFileContent(fileContextInfo.caretContext.leftFileContext)
                .rightFileContent(fileContextInfo.caretContext.rightFileContext)
                .filename(fileContextInfo.fileRelativePath ?: fileContextInfo.filename)
                .programmingLanguage(programmingLanguage)
                .build()
            val supplementalContexts = supplementalContext?.contents?.map {
                SupplementalContext.builder()
                    .content(it.content)
                    .filePath(it.path)
                    .build()
            }.orEmpty()
            val includeCodeWithReference = if (CodeWhispererSettings.getInstance().isIncludeCodeWithReference()) {
                RecommendationsWithReferencesPreference.ALLOW
            } else {
                RecommendationsWithReferencesPreference.BLOCK
            }

            return GenerateCompletionsRequest.builder()
                .fileContext(fileContext)
                .supplementalContexts(supplementalContexts)
                .referenceTrackerConfiguration { it.recommendationsWithReferences(includeCodeWithReference) }
                .customizationArn(customizationArn)
                .optOutPreference(getTelemetryOptOutPreference())
                .build()
        }
    }
}

data class RequestContextNew(
    val project: Project,
    val editor: Editor,
    val triggerTypeInfo: TriggerTypeInfo,
    val caretPosition: CaretPosition,
    val fileContextInfo: FileContextInfo,
    private val supplementalContextDeferred: Deferred<SupplementalContextInfo?>,
    val connection: ToolkitConnection?,
    val customizationArn: String?,
) {
    // TODO: should make the entire getRequestContext() suspend function instead of making supplemental context only
    var supplementalContext: SupplementalContextInfo? = null
        private set
        get() = when (field) {
            null -> {
                if (!supplementalContextDeferred.isCompleted) {
                    error("attempt to access supplemental context before awaiting the deferred")
                } else {
                    null
                }
            }
            else -> field
        }

    suspend fun awaitSupplementalContext(): SupplementalContextInfo? {
        supplementalContext = supplementalContextDeferred.await()
        return supplementalContext
    }
}

interface CodeWhispererIntelliSenseOnHoverListener {
    fun onEnter() {}
}
