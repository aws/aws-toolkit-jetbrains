// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.launch
import org.apache.commons.collections4.queue.CircularFifoQueue
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.Completion
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCodeWhispererStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getGettingStartedTaskType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.CodewhispererCodeScanScope
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererGettingStartedTask
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererPreviousSuggestionState
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant
import java.util.Queue

@Service
class CodeWhispererTelemetryServiceNew {
    // store previous 5 userTrigger decisions
    private val previousUserTriggerDecisions = CircularFifoQueue<CodewhispererPreviousSuggestionState>(5)

    private var previousUserTriggerDecisionTimestamp: Instant? = null

    private val codewhispererTimeSinceLastUserDecision: Double? =
        previousUserTriggerDecisionTimestamp?.let {
            Duration.between(it, Instant.now()).toMillis().toDouble()
        }

    val previousUserTriggerDecision: CodewhispererPreviousSuggestionState?
        get() = if (previousUserTriggerDecisions.isNotEmpty()) previousUserTriggerDecisions.last() else null

    companion object {
        fun getInstance(): CodeWhispererTelemetryServiceNew = service()
        val LOG = getLogger<CodeWhispererTelemetryServiceNew>()
        const val NO_ACCEPTED_INDEX = -1
    }

    fun sendFailedServiceInvocationEvent(project: Project, exceptionType: String?) {
        CodewhispererTelemetry.serviceInvocation(
            project = project,
            codewhispererCursorOffset = 0,
            codewhispererLanguage = CodewhispererLanguage.Unknown,
            codewhispererLastSuggestionIndex = -1,
            codewhispererLineNumber = 0,
            codewhispererTriggerType = CodewhispererTriggerType.Unknown,
            duration = 0.0,
            reason = exceptionType,
            success = false,
        )
    }

    fun sendServiceInvocationEvent(
        jobId: Int,
        requestId: String,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        lastRecommendationIndex: Int,
        invocationSuccess: Boolean,
        latency: Double,
        exceptionType: String?,
    ) {
        LOG.debug { "Sending serviceInvocation for $requestId, jobId: $jobId" }
        val (triggerType, automatedTriggerType) = requestContext.triggerTypeInfo
        val (offset, line) = requestContext.caretPosition

        // since python now only supports UTG but not cross file context
        val supContext = if (requestContext.fileContextInfo.programmingLanguage.isUTGSupported() &&
            requestContext.supplementalContext?.isUtg == true
        ) {
            requestContext.supplementalContext
        } else if (requestContext.fileContextInfo.programmingLanguage.isSupplementalContextSupported() &&
            requestContext.supplementalContext?.isUtg == false
        ) {
            requestContext.supplementalContext
        } else {
            null
        }

        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toTelemetryType()
        val startUrl = getConnectionStartUrl(requestContext.connection)
        CodewhispererTelemetry.serviceInvocation(
            project = requestContext.project,
            codewhispererAutomatedTriggerType = automatedTriggerType.telemetryType,
            codewhispererCompletionType = CodewhispererCompletionType.Line,
            codewhispererCursorOffset = offset.toLong(),
            codewhispererGettingStartedTask = getGettingStartedTaskType(requestContext.editor),
            codewhispererLanguage = codewhispererLanguage,
            codewhispererLastSuggestionIndex = lastRecommendationIndex.toLong(),
            codewhispererLineNumber = line.toLong(),
            codewhispererRequestId = requestId,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererTriggerType = triggerType,
            duration = latency,
            reason = exceptionType,
            success = invocationSuccess,
            credentialStartUrl = startUrl,
            codewhispererImportRecommendationEnabled = CodeWhispererSettings.getInstance().isImportAdderEnabled(),
            codewhispererSupplementalContextTimeout = supContext?.isProcessTimeout,
            codewhispererSupplementalContextIsUtg = supContext?.isUtg,
            codewhispererSupplementalContextLatency = supContext?.latency?.toDouble(),
            codewhispererSupplementalContextLength = supContext?.contentLength?.toLong(),
            codewhispererCustomizationArn = requestContext.customizationArn,
        )
    }

    fun sendUserDecisionEvent(
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        detailContext: DetailContextNew,
        index: Int,
        suggestionState: CodewhispererSuggestionState,
        numOfRecommendations: Int,
    ) {
        val requestId = detailContext.requestId
        val recommendation = detailContext.recommendation
        val (project, _, triggerTypeInfo) = requestContext
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toTelemetryType()
        val supplementalContext = requestContext.supplementalContext

        LOG.debug {
            "Recording user decisions of recommendation. " +
                "Index: $index, " +
                "State: $suggestionState, " +
                "Request ID: $requestId, " +
                "Recommendation: ${recommendation.content()}"
        }
        val startUrl = getConnectionStartUrl(requestContext.connection)
        val importEnabled = CodeWhispererSettings.getInstance().isImportAdderEnabled()
        CodewhispererTelemetry.userDecision(
            project = project,
            codewhispererCompletionType = detailContext.completionType,
            codewhispererGettingStartedTask = getGettingStartedTaskType(requestContext.editor),
            codewhispererLanguage = codewhispererLanguage,
            codewhispererPaginationProgress = numOfRecommendations.toLong(),
            codewhispererRequestId = requestId,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererSuggestionIndex = index.toLong(),
            codewhispererSuggestionReferenceCount = recommendation.references().size.toLong(),
            codewhispererSuggestionReferences = jacksonObjectMapper().writeValueAsString(recommendation.references().map { it.licenseName() }.toSet().toList()),
            codewhispererSuggestionImportCount = if (importEnabled) recommendation.mostRelevantMissingImports().size.toLong() else null,
            codewhispererSuggestionState = suggestionState,
            codewhispererTriggerType = triggerTypeInfo.triggerType,
            credentialStartUrl = startUrl,
            codewhispererSupplementalContextIsUtg = supplementalContext?.isUtg,
            codewhispererSupplementalContextLength = supplementalContext?.contentLength?.toLong(),
            codewhispererSupplementalContextTimeout = supplementalContext?.isProcessTimeout,
        )
    }

    fun sendUserTriggerDecisionEvent(
        sessionContext: SessionContextNew,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        recommendationContext: RecommendationContextNew,
        suggestionState: CodewhispererSuggestionState,
        popupShownTime: Duration?,
        suggestionReferenceCount: Int,
        generatedLineCount: Int,
        acceptedCharCount: Int,
    ) {
        val project = requestContext.project
        val totalImportCount = recommendationContext.details.fold(0) { grandTotal, detail ->
            grandTotal + detail.recommendation.mostRelevantMissingImports().size
        }

        val automatedTriggerType = requestContext.triggerTypeInfo.automatedTriggerType
        val triggerChar = if (automatedTriggerType is CodeWhispererAutomatedTriggerType.SpecialChar) {
            automatedTriggerType.specialChar.toString()
        } else {
            null
        }

        val language = requestContext.fileContextInfo.programmingLanguage

        val classifierResult = requestContext.triggerTypeInfo.automatedTriggerType.calculationResult

        val classifierThreshold = CodeWhispererAutoTriggerService.getThreshold()

        val supplementalContext = requestContext.supplementalContext
        val completionType = if (recommendationContext.details.isEmpty()) CodewhispererCompletionType.Line else recommendationContext.details[0].completionType

        // only send if it's a pro tier user
        projectCoroutineScope(project).launch {
            runIfIdcConnectionOrTelemetryEnabled(project) {
                try {
                    val response = CodeWhispererClientAdaptor.getInstance(project)
                        .sendUserTriggerDecisionTelemetry(
                            sessionContext,
                            requestContext,
                            responseContext,
                            completionType,
                            suggestionState,
                            suggestionReferenceCount,
                            generatedLineCount,
                            recommendationContext.details.size,
                            acceptedCharCount
                        )
                    LOG.debug {
                        "Successfully sent user trigger decision telemetry. RequestId: ${response.responseMetadata().requestId()}"
                    }
                } catch (e: Exception) {
                    val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                    LOG.debug {
                        "Failed to send user trigger decision telemetry. RequestId: $requestId, ErrorMessage: ${e.message}"
                    }
                }
            }
        }

        CodewhispererTelemetry.userTriggerDecision(
            project = project,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererFirstRequestId = sessionContext.latencyContext.firstRequestId,
            credentialStartUrl = getConnectionStartUrl(requestContext.connection),
            codewhispererIsPartialAcceptance = null,
            codewhispererPartialAcceptanceCount = null,
            codewhispererCharactersAccepted = acceptedCharCount.toLong(),
            codewhispererCharactersRecommended = null,
            codewhispererCompletionType = completionType,
            codewhispererLanguage = language.toTelemetryType(),
            codewhispererTriggerType = requestContext.triggerTypeInfo.triggerType,
            codewhispererAutomatedTriggerType = automatedTriggerType.telemetryType,
            codewhispererLineNumber = requestContext.caretPosition.line.toLong(),
            codewhispererCursorOffset = requestContext.caretPosition.offset.toLong(),
            codewhispererSuggestionCount = recommendationContext.details.size.toLong(),
            codewhispererSuggestionImportCount = totalImportCount.toLong(),
            codewhispererTotalShownTime = popupShownTime?.toMillis()?.toDouble(),
            codewhispererTriggerCharacter = triggerChar,
            codewhispererTypeaheadLength = recommendationContext.userInputSinceInvocation.length.toLong(),
            codewhispererTimeSinceLastDocumentChange = CodeWhispererInvocationStatus.getInstance().getTimeSinceDocumentChanged(),
            codewhispererTimeSinceLastUserDecision = codewhispererTimeSinceLastUserDecision,
            codewhispererTimeToFirstRecommendation = sessionContext.latencyContext.paginationFirstCompletionTime,
            codewhispererPreviousSuggestionState = previousUserTriggerDecision,
            codewhispererSuggestionState = suggestionState,
            codewhispererClassifierResult = classifierResult,
            codewhispererClassifierThreshold = classifierThreshold,
            codewhispererCustomizationArn = requestContext.customizationArn,
            codewhispererSupplementalContextIsUtg = supplementalContext?.isUtg,
            codewhispererSupplementalContextLength = supplementalContext?.contentLength?.toLong(),
            codewhispererSupplementalContextTimeout = supplementalContext?.isProcessTimeout,
            codewhispererSupplementalContextStrategyId = supplementalContext?.strategy.toString(),
            codewhispererGettingStartedTask = getGettingStartedTaskType(requestContext.editor),
            codewhispererFeatureEvaluations = CodeWhispererFeatureConfigService.getInstance().getFeatureConfigsTelemetry()
        )
    }

    fun sendSecurityScanEvent(codeScanEvent: CodeScanTelemetryEvent, project: Project? = null) {
        val payloadContext = codeScanEvent.codeScanResponseContext.payloadContext
        val serviceInvocationContext = codeScanEvent.codeScanResponseContext.serviceInvocationContext
        val codeScanJobId = codeScanEvent.codeScanResponseContext.codeScanJobId
        val totalIssues = codeScanEvent.codeScanResponseContext.codeScanTotalIssues
        val issuesWithFixes = codeScanEvent.codeScanResponseContext.codeScanIssuesWithFixes
        val reason = codeScanEvent.codeScanResponseContext.reason
        val startUrl = getConnectionStartUrl(codeScanEvent.connection)
        val codeAnalysisScope = codeScanEvent.codeAnalysisScope
        val passive = codeAnalysisScope == CodeWhispererConstants.CodeAnalysisScope.FILE

        LOG.debug {
            "Recording code security scan event. \n" +
                "Total number of security scan issues found: $totalIssues, \n" +
                "Number of security scan issues with fixes: $issuesWithFixes, \n" +
                "Language: ${payloadContext.language}, \n" +
                "Uncompressed source payload size in bytes: ${payloadContext.srcPayloadSize}, \n" +
                "Uncompressed build payload size in bytes: ${payloadContext.buildPayloadSize}, \n" +
                "Compressed source zip file size in bytes: ${payloadContext.srcZipFileSize}, \n" +
                "Total project size in bytes: ${codeScanEvent.totalProjectSizeInBytes}, \n" +
                "Total duration of the security scan job in milliseconds: ${codeScanEvent.duration}, \n" +
                "Context truncation duration in milliseconds: ${payloadContext.totalTimeInMilliseconds}, \n" +
                "Artifacts upload duration in milliseconds: ${serviceInvocationContext.artifactsUploadDuration}, \n" +
                "Service invocation duration in milliseconds: ${serviceInvocationContext.serviceInvocationDuration}, \n" +
                "Total number of lines scanned: ${payloadContext.totalLines}, \n" +
                "Reason: $reason \n" +
                "Scope: ${codeAnalysisScope.value} \n" +
                "Passive: $passive \n"
        }
        CodewhispererTelemetry.securityScan(
            project = project,
            codewhispererCodeScanLines = payloadContext.totalLines,
            codewhispererCodeScanJobId = codeScanJobId,
            codewhispererCodeScanProjectBytes = codeScanEvent.totalProjectSizeInBytes,
            codewhispererCodeScanSrcPayloadBytes = payloadContext.srcPayloadSize,
            codewhispererCodeScanBuildPayloadBytes = payloadContext.buildPayloadSize,
            codewhispererCodeScanSrcZipFileBytes = payloadContext.srcZipFileSize,
            codewhispererCodeScanTotalIssues = totalIssues.toLong(),
            codewhispererCodeScanIssuesWithFixes = issuesWithFixes.toLong(),
            codewhispererLanguage = payloadContext.language,
            duration = codeScanEvent.duration,
            contextTruncationDuration = payloadContext.totalTimeInMilliseconds,
            artifactsUploadDuration = serviceInvocationContext.artifactsUploadDuration,
            codeScanServiceInvocationsDuration = serviceInvocationContext.serviceInvocationDuration,
            reason = reason,
            result = codeScanEvent.result,
            credentialStartUrl = startUrl,
            codewhispererCodeScanScope = CodewhispererCodeScanScope.from(codeAnalysisScope.value),
            passive = passive
        )
    }

    fun sendCodeScanIssueHoverEvent(issue: CodeWhispererCodeScanIssue) {
        CodewhispererTelemetry.codeScanIssueHover(
            findingId = issue.findingId,
            detectorId = issue.detectorId,
            ruleId = issue.ruleId,
            includesFix = issue.suggestedFixes.isNotEmpty(),
            credentialStartUrl = getCodeWhispererStartUrl(issue.project)
        )
    }

    fun sendCodeScanIssueApplyFixEvent(issue: CodeWhispererCodeScanIssue, result: Result, reason: String? = null) {
        CodewhispererTelemetry.codeScanIssueApplyFix(
            findingId = issue.findingId,
            detectorId = issue.detectorId,
            ruleId = issue.ruleId,
            component = Component.Hover,
            result = result,
            reason = reason,
            credentialStartUrl = getCodeWhispererStartUrl(issue.project)
        )
    }

    fun enqueueAcceptedSuggestionEntry(
        requestId: String,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        time: Instant,
        vFile: VirtualFile?,
        range: RangeMarker,
        suggestion: String,
        selectedIndex: Int,
        completionType: CodewhispererCompletionType,
    ) {
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage
        CodeWhispererUserModificationTracker.getInstance(requestContext.project).enqueue(
            AcceptedSuggestionEntry(
                time,
                vFile,
                range,
                suggestion,
                responseContext.sessionId,
                requestId,
                selectedIndex,
                requestContext.triggerTypeInfo.triggerType,
                completionType,
                codewhispererLanguage,
                null,
                null,
                requestContext.connection
            )
        )
    }

    fun sendUserDecisionEventForAll(
        sessionContext: SessionContextNew,
        hasUserAccepted: Boolean,
        popupShownTime: Duration? = null,
    ) {
        CodeWhispererServiceNew.getInstance().getAllPaginationSessions().forEach { (jobId, state) ->
            if (state == null) return@forEach
            val details = state.recommendationContext.details

            val decisions = details.mapIndexed { index, detail ->
                val suggestionState = recordSuggestionState(detail, hasUserAccepted)
                sendUserDecisionEvent(state.requestContext, state.responseContext, detail, index, suggestionState, details.size)

                suggestionState
            }
            LOG.debug { "jobId: $jobId, userDecisions: [${decisions.joinToString(", ")}]" }

            with(aggregateUserDecision(decisions)) {
                // the order of the following matters
                // step 1, send out current decision
                LOG.debug { "jobId: $jobId, userTriggerDecision: $this" }
                previousUserTriggerDecisionTimestamp = Instant.now()

                val previews = CodeWhispererServiceNew.getInstance().getAllSuggestionsPreviewInfo()
                val recommendation =
                    if (hasUserAccepted) {
                        previews[sessionContext.selectedIndex].detail.recommendation
                    } else {
                        Completion.builder().content("").references(emptyList()).build()
                    }
                val referenceCount = if (hasUserAccepted && recommendation.hasReferences()) 1 else 0
                val acceptedContent = recommendation.content()
                val generatedLineCount = if (acceptedContent.isEmpty()) 0 else acceptedContent.split("\n").size
                val acceptedCharCount = acceptedContent.length
                sendUserTriggerDecisionEvent(
                    sessionContext,
                    state.requestContext,
                    state.responseContext,
                    state.recommendationContext,
                    this,
                    popupShownTime,
                    referenceCount,
                    generatedLineCount,
                    acceptedCharCount
                )

                // step 2, put current decision into queue for later reference
                if (this != CodewhispererSuggestionState.Ignore && this != CodewhispererSuggestionState.Unseen) {
                    val previousState = CodewhispererPreviousSuggestionState.from(this.toString())
                    // we need this as well because AutoTriggerService will reset the queue periodically
                    previousUserTriggerDecisions.add(previousState)
                    CodeWhispererAutoTriggerService.getInstance().addPreviousDecision(previousState)
                }
            }
        }
    }

    /**
     * Aggregate recommendation level user decision to trigger level user decision based on the following rule
     * - Accept if there is an Accept
     * - Reject if there is a Reject
     * - Empty if all decisions are Empty
     * - Ignore if at least one suggestion is seen and there's an accept for another trigger in the same display session
     * - Unseen if the whole trigger is not seen (but has valid suggestions)
     * - Record the accepted suggestion index
     * - Discard otherwise
     */
    fun aggregateUserDecision(decisions: List<CodewhispererSuggestionState>): CodewhispererSuggestionState {
        var isEmpty = true
        var isUnseen = true
        var isDiscard = true

        for (decision in decisions) {
            if (decision == CodewhispererSuggestionState.Accept) {
                return CodewhispererSuggestionState.Accept
            } else if (decision == CodewhispererSuggestionState.Reject) {
                return CodewhispererSuggestionState.Reject
            } else if (decision == CodewhispererSuggestionState.Unseen) {
                isEmpty = false
                isDiscard = false
            } else if (decision == CodewhispererSuggestionState.Ignore) {
                isUnseen = false
                isEmpty = false
                isDiscard = false
            } else if (decision == CodewhispererSuggestionState.Discard) {
                isEmpty = false
            }
        }

        return if (isEmpty) {
            CodewhispererSuggestionState.Empty
        } else if (isDiscard) {
            CodewhispererSuggestionState.Discard
        } else if (isUnseen) {
            CodewhispererSuggestionState.Unseen
        } else {
            CodewhispererSuggestionState.Ignore
        }
    }

    fun sendOnboardingClickEvent(language: CodeWhispererProgrammingLanguage, taskType: CodewhispererGettingStartedTask) {
        // Project instance is not needed. We look at these metrics for each clientId.
        CodewhispererTelemetry.onboardingClick(project = null, codewhispererLanguage = language.toTelemetryType(), codewhispererGettingStartedTask = taskType)
    }

    fun recordSuggestionState(
        detail: DetailContextNew,
        hasUserAccepted: Boolean,
    ): CodewhispererSuggestionState =
        if (detail.recommendation.content().isEmpty()) {
            CodewhispererSuggestionState.Empty
        } else if (detail.isDiscarded) {
            CodewhispererSuggestionState.Discard
        } else if (!detail.hasSeen) {
            CodewhispererSuggestionState.Unseen
        } else if (hasUserAccepted) {
            if (detail.isAccepted) {
                CodewhispererSuggestionState.Accept
            } else {
                CodewhispererSuggestionState.Ignore
            }
        } else {
            CodewhispererSuggestionState.Reject
        }

    @TestOnly
    fun previousDecisions(): Queue<CodewhispererPreviousSuggestionState> {
        assert(ApplicationManager.getApplication().isUnitTestMode)
        return this.previousUserTriggerDecisions
    }
}
