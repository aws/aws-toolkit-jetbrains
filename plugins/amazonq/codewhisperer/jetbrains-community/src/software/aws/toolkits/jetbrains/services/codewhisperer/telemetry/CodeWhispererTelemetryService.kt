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
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCodeWhispererStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getGettingStartedTaskType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.CodeFixAction
import software.aws.toolkits.telemetry.CodewhispererCodeScanScope
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererGettingStartedTask
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererPreviousSuggestionState
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Result
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Queue
import kotlin.io.path.pathString

@Service
class CodeWhispererTelemetryService {
    // store previous 5 userTrigger decisions
    private val previousUserTriggerDecisions = CircularFifoQueue<CodewhispererPreviousSuggestionState>(5)

    private var previousUserTriggerDecisionTimestamp: Instant? = null

    private val codewhispererTimeSinceLastUserDecision: Double?
        get() {
            return previousUserTriggerDecisionTimestamp?.let {
                Duration.between(it, Instant.now()).toMillis().toDouble()
            }
        }

    val previousUserTriggerDecision: CodewhispererPreviousSuggestionState?
        get() = if (previousUserTriggerDecisions.isNotEmpty()) previousUserTriggerDecisions.last() else null

    companion object {
        fun getInstance(): CodeWhispererTelemetryService = service()
        val LOG = getLogger<CodeWhispererTelemetryService>()
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
        requestId: String,
        requestContext: RequestContext,
        responseContext: ResponseContext,
        lastRecommendationIndex: Int,
        invocationSuccess: Boolean,
        latency: Double,
        exceptionType: String?,
    ) {
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
        requestContext: RequestContext,
        responseContext: ResponseContext,
        detailContext: DetailContext,
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
        requestContext: RequestContext,
        responseContext: ResponseContext,
        recommendationContext: RecommendationContext,
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
            codewhispererFirstRequestId = requestContext.latencyContext.firstRequestId,
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
            codewhispererTimeToFirstRecommendation = requestContext.latencyContext.paginationFirstCompletionTime,
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

    private fun mapToTelemetryScope(codeAnalysisScope: CodeWhispererConstants.CodeAnalysisScope, initiatedByChat: Boolean): CodewhispererCodeScanScope =
        when (codeAnalysisScope) {
            CodeWhispererConstants.CodeAnalysisScope.FILE -> {
                if (initiatedByChat) {
                    CodewhispererCodeScanScope.FILEONDEMAND
                } else {
                    CodewhispererCodeScanScope.FILEAUTO
                }
            }
            CodeWhispererConstants.CodeAnalysisScope.PROJECT -> CodewhispererCodeScanScope.PROJECT
        }

    fun sendSecurityScanEvent(codeScanEvent: CodeScanTelemetryEvent, project: Project? = null) {
        val payloadContext = codeScanEvent.codeScanResponseContext.payloadContext
        val serviceInvocationContext = codeScanEvent.codeScanResponseContext.serviceInvocationContext
        val codeScanJobId = codeScanEvent.codeScanResponseContext.codeScanJobId
        val totalIssues = codeScanEvent.codeScanResponseContext.codeScanTotalIssues
        val issuesWithFixes = codeScanEvent.codeScanResponseContext.codeScanIssuesWithFixes
        val reason = codeScanEvent.codeScanResponseContext.reason
        val startUrl = getConnectionStartUrl(codeScanEvent.connection)
        val codeAnalysisScope = mapToTelemetryScope(codeScanEvent.codeAnalysisScope, codeScanEvent.initiatedByChat)
        val passive = codeAnalysisScope == CodewhispererCodeScanScope.FILEAUTO
        val source = if (codeScanEvent.initiatedByChat) "chat" else "menu"

        LOG.debug {
            "Recording code security scan event. \n" +
                "Total number of security scan issues found: $totalIssues, \n" +
                "Number of security scan issues with fixes: $issuesWithFixes, \n" +
                "Language: ${payloadContext.language}, \n" +
                "Uncompressed source payload size in bytes: ${payloadContext.srcPayloadSize}, \n" +
                "Compressed source zip file size in bytes: ${payloadContext.srcZipFileSize}, \n" +
                "Total project size in bytes: ${codeScanEvent.totalProjectSizeInBytes}, \n" +
                "Total duration of the security scan job in milliseconds: ${codeScanEvent.duration}, \n" +
                "Context truncation duration in milliseconds: ${payloadContext.totalTimeInMilliseconds}, \n" +
                "Artifacts upload duration in milliseconds: ${serviceInvocationContext.artifactsUploadDuration}, \n" +
                "Service invocation duration in milliseconds: ${serviceInvocationContext.serviceInvocationDuration}, \n" +
                "Total number of lines scanned: ${payloadContext.totalLines}, \n" +
                "Reason: $reason \n" +
                "Scope: $codeAnalysisScope \n" +
                "Passive: $passive \n" +
                "Source: $source \n"
        }
        CodewhispererTelemetry.securityScan(
            project = project,
            codewhispererCodeScanLines = payloadContext.totalLines,
            codewhispererCodeScanJobId = codeScanJobId,
            codewhispererCodeScanProjectBytes = codeScanEvent.totalProjectSizeInBytes,
            codewhispererCodeScanSrcPayloadBytes = payloadContext.srcPayloadSize,
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
            codewhispererCodeScanScope = codeAnalysisScope,
            passive = passive,
            source = source
        )
    }

    fun sendCodeScanIssueHoverEvent(issue: CodeWhispererCodeScanIssue) {
        CodewhispererTelemetry.codeScanIssueHover(
            findingId = issue.findingId,
            detectorId = issue.detectorId,
            ruleId = issue.ruleId,
            includesFix = issue.suggestedFixes.isNotEmpty(),
            credentialStartUrl = getCodeWhispererStartUrl(issue.project),
            autoDetected = issue.autoDetected
        )
    }

    fun sendCodeScanIssueApplyFixEvent(issue: CodeWhispererCodeScanIssue, result: Result, reason: String? = null, codeFixAction: CodeFixAction?) {
        CodewhispererTelemetry.codeScanIssueApplyFix(
            findingId = issue.findingId,
            detectorId = issue.detectorId,
            ruleId = issue.ruleId,
            component = Component.Hover,
            result = result,
            reason = reason,
            credentialStartUrl = getCodeWhispererStartUrl(issue.project),
            codeFixAction = codeFixAction,
            autoDetected = issue.autoDetected,
            codewhispererCodeScanJobId = issue.scanJobId
        )
    }

    fun sendCodeScanNewTabEvent(credentialSourceId: CredentialSourceId?) {
        CodewhispererTelemetry.codeScanChatNewTab(
            credentialSourceId = credentialSourceId
        )
    }

    fun sendCodeScanIssueIgnore(
        component: Component,
        issue: CodeWhispererCodeScanIssue,
        isIgnoreAll: Boolean,
    ) {
        CodewhispererTelemetry.codeScanIssueIgnore(
            component = component,
            credentialStartUrl = getCodeWhispererStartUrl(issue.project),
            findingId = issue.findingId,
            detectorId = issue.detectorId,
            ruleId = issue.ruleId,
            variant = if (isIgnoreAll) "all" else null,
            autoDetected = issue.autoDetected
        )
    }

    fun sendCodeScanIssueGenerateFix(
        component: Component,
        issue: CodeWhispererCodeScanIssue,
        isRefresh: Boolean,
        result: MetricResult,
        reason: String? = null,
        includesFix: Boolean? = false,
    ) {
        CodewhispererTelemetry.codeScanIssueGenerateFix(
            component = component,
            credentialStartUrl = getCodeWhispererStartUrl(issue.project),
            findingId = issue.findingId,
            detectorId = issue.detectorId,
            ruleId = issue.ruleId,
            variant = if (isRefresh) "refresh" else null,
            result = result,
            reason = reason,
            autoDetected = issue.autoDetected,
            codewhispererCodeScanJobId = issue.scanJobId,
            includesFix = includesFix
        )
    }

    fun enqueueAcceptedSuggestionEntry(
        requestId: String,
        requestContext: RequestContext,
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
        requestContext: RequestContext,
        responseContext: ResponseContext,
        recommendationContext: RecommendationContext,
        sessionContext: SessionContext,
        hasUserAccepted: Boolean,
        popupShownTime: Duration? = null,
    ) {
        val detailContexts = recommendationContext.details
        val decisions = mutableListOf<CodewhispererSuggestionState>()

        detailContexts.forEachIndexed { index, detailContext ->
            val suggestionState = recordSuggestionState(
                index,
                sessionContext.selectedIndex,
                sessionContext.seen.contains(index),
                hasUserAccepted,
                detailContext.isDiscarded,
                detailContext.recommendation.content().isEmpty()
            )
            sendUserDecisionEvent(requestContext, responseContext, detailContext, index, suggestionState, detailContexts.size)

            decisions.add(suggestionState)
        }

        with(aggregateUserDecision(decisions)) {
            // the order of the following matters
            // step 1, send out current decision
            previousUserTriggerDecisionTimestamp = Instant.now()

            val referenceCount = if (hasUserAccepted && detailContexts[sessionContext.selectedIndex].recommendation.hasReferences()) 1 else 0
            val acceptedContent =
                if (hasUserAccepted) {
                    detailContexts[sessionContext.selectedIndex].recommendation.content()
                } else {
                    ""
                }
            val generatedLineCount = if (acceptedContent.isEmpty()) 0 else acceptedContent.split("\n").size
            val acceptedCharCount = acceptedContent.length
            sendUserTriggerDecisionEvent(
                requestContext,
                responseContext,
                recommendationContext,
                CodewhispererSuggestionState.from(this.toString()),
                popupShownTime,
                referenceCount,
                generatedLineCount,
                acceptedCharCount
            )

            // step 2, put current decision into queue for later reference
            previousUserTriggerDecisions.add(this)
            // we need this as well because AutotriggerService will reset the queue periodically
            CodeWhispererAutoTriggerService.getInstance().addPreviousDecision(this)
        }
    }

    /**
     * Aggregate recommendation level user decision to trigger level user decision based on the following rule
     * - Accept if there is an Accept
     * - Reject if there is a Reject
     * - Empty if all decisions are Empty
     * - Record the accepted suggestion index
     * - Discard otherwise
     */
    fun aggregateUserDecision(decisions: List<CodewhispererSuggestionState>): CodewhispererPreviousSuggestionState {
        var isEmpty = true

        for (decision in decisions) {
            if (decision == CodewhispererSuggestionState.Accept) {
                return CodewhispererPreviousSuggestionState.Accept
            } else if (decision == CodewhispererSuggestionState.Reject) {
                return CodewhispererPreviousSuggestionState.Reject
            } else if (decision != CodewhispererSuggestionState.Empty) {
                isEmpty = false
            }
        }

        return if (isEmpty) {
            CodewhispererPreviousSuggestionState.Empty
        } else {
            CodewhispererPreviousSuggestionState.Discard
        }
    }

    fun sendPerceivedLatencyEvent(
        requestId: String,
        requestContext: RequestContext,
        responseContext: ResponseContext,
        latency: Double,
    ) {
        val (project, _, triggerTypeInfo) = requestContext
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toTelemetryType()
        val startUrl = getConnectionStartUrl(requestContext.connection)
        CodewhispererTelemetry.perceivedLatency(
            project = project,
            codewhispererCompletionType = CodewhispererCompletionType.Line,
            codewhispererLanguage = codewhispererLanguage,
            codewhispererRequestId = requestId,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererTriggerType = triggerTypeInfo.triggerType,
            duration = latency,
            passive = true,
            credentialStartUrl = startUrl,
            codewhispererCustomizationArn = requestContext.customizationArn,
        )
    }

    fun sendClientComponentLatencyEvent(states: InvocationContext) {
        val requestContext = states.requestContext
        val responseContext = states.responseContext
        val codewhispererLanguage = requestContext.fileContextInfo.programmingLanguage.toTelemetryType()
        val startUrl = getConnectionStartUrl(requestContext.connection)
        CodewhispererTelemetry.clientComponentLatency(
            project = requestContext.project,
            codewhispererSessionId = responseContext.sessionId,
            codewhispererRequestId = requestContext.latencyContext.firstRequestId,
            codewhispererFirstCompletionLatency = requestContext.latencyContext.paginationFirstCompletionTime,
            codewhispererPreprocessingLatency = requestContext.latencyContext.getCodeWhispererPreprocessingLatency(),
            codewhispererEndToEndLatency = requestContext.latencyContext.getCodeWhispererEndToEndLatency(),
            codewhispererAllCompletionsLatency = requestContext.latencyContext.getCodeWhispererAllCompletionsLatency(),
            codewhispererPostprocessingLatency = requestContext.latencyContext.getCodeWhispererPostprocessingLatency(),
            codewhispererCredentialFetchingLatency = requestContext.latencyContext.getCodeWhispererCredentialFetchingLatency(),
            codewhispererTriggerType = requestContext.triggerTypeInfo.triggerType,
            codewhispererCompletionType = CodewhispererCompletionType.Line,
            codewhispererLanguage = codewhispererLanguage,
            credentialStartUrl = startUrl,
            codewhispererCustomizationArn = requestContext.customizationArn,
        )
    }

    fun sendOnboardingClickEvent(language: CodeWhispererProgrammingLanguage, taskType: CodewhispererGettingStartedTask) {
        // Project instance is not needed. We look at these metrics for each clientId.
        CodewhispererTelemetry.onboardingClick(project = null, codewhispererLanguage = language.toTelemetryType(), codewhispererGettingStartedTask = taskType)
    }

    fun recordSuggestionState(
        index: Int,
        selectedIndex: Int,
        hasSeen: Boolean,
        hasUserAccepted: Boolean,
        isDiscarded: Boolean,
        isEmpty: Boolean,
    ): CodewhispererSuggestionState =
        if (isEmpty) {
            CodewhispererSuggestionState.Empty
        } else if (isDiscarded) {
            CodewhispererSuggestionState.Discard
        } else if (!hasSeen) {
            CodewhispererSuggestionState.Unseen
        } else if (hasUserAccepted) {
            if (selectedIndex == index) {
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

    fun sendInvalidZipEvent(filePath: Path, projectRoot: Path, relativePath: String) {
        CodewhispererTelemetry.invalidZip(
            filePath = filePath.pathString,
            workspaceRoot = projectRoot.pathString,
            relativePath = relativePath
        )
    }
}

fun isTelemetryEnabled(): Boolean = AwsSettings.getInstance().isTelemetryEnabled
