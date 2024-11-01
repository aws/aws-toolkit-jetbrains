// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.launch
import org.apache.commons.collections4.queue.CircularFifoQueue
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.Completion
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.model.DetailContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getGettingStartedTaskType
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererPreviousSuggestionState
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import software.aws.toolkits.telemetry.Telemetry
import java.time.Duration
import java.time.Instant

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
    }

    @Deprecated(
        "Does not capture entire context of method call",
        ReplaceWith("Telemetry.codewhisperer.serviceInvocation", "software.aws.toolkits.telemetry.Telemetry")
    )
    fun sendFailedServiceInvocationEvent(e: Exception): Unit = Telemetry.codewhisperer.serviceInvocation.use {
        it.codewhispererCursorOffset(0)
            .codewhispererLanguage(CodewhispererLanguage.Unknown)
            .codewhispererLastSuggestionIndex(-1L)
            .codewhispererLineNumber(0)
            .codewhispererTriggerType(CodewhispererTriggerType.Unknown)
            .duration(0.0)
            .recordException(e)
            .success(false)
    }

    @Deprecated(
        "Does not capture entire context of method call",
        ReplaceWith("Telemetry.codewhisperer.serviceInvocation", "software.aws.toolkits.telemetry.Telemetry")
    )
    fun sendServiceInvocationEvent(
        jobId: Int,
        requestId: String,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        lastRecommendationIndex: Int,
        invocationSuccess: Boolean,
        latency: Double,
        exceptionType: String?,
    ): Unit = Telemetry.codewhisperer.serviceInvocation.use {
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

        it.codewhispererAutomatedTriggerType(automatedTriggerType.telemetryType)
            .codewhispererCompletionType(CodewhispererCompletionType.Line)
            .codewhispererCursorOffset(offset)
            .codewhispererGettingStartedTask(getGettingStartedTaskType(requestContext.editor))
            .codewhispererLanguage(requestContext.fileContextInfo.programmingLanguage.toTelemetryType())
            .codewhispererLastSuggestionIndex(lastRecommendationIndex)
            .codewhispererLineNumber(line)
            .codewhispererRequestId(requestId)
            .codewhispererSessionId(responseContext.sessionId)
            .codewhispererTriggerType(triggerType)
            .duration(latency)
            .reason(exceptionType)
            .success(invocationSuccess)
            .credentialStartUrl(getConnectionStartUrl(requestContext.connection))
            .codewhispererImportRecommendationEnabled(CodeWhispererSettings.getInstance().isImportAdderEnabled())
            .codewhispererSupplementalContextTimeout(supContext?.isProcessTimeout)
            .codewhispererSupplementalContextIsUtg(supContext?.isUtg)
            .codewhispererSupplementalContextLatency(supContext?.latency?.toDouble())
            .codewhispererSupplementalContextLength(supContext?.contentLength?.toLong())
            .codewhispererCustomizationArn(requestContext.customizationArn)
    }

    @Deprecated(
        "Does not capture entire context of method call",
        ReplaceWith("Telemetry.codewhisperer.userDecision", "software.aws.toolkits.telemetry.Telemetry")
    )
    fun sendUserDecisionEvent(
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        detailContext: DetailContextNew,
        index: Int,
        suggestionState: CodewhispererSuggestionState,
        numOfRecommendations: Int,
    ): Unit = Telemetry.codewhisperer.userDecision.use {
        val requestId = detailContext.requestId
        val recommendation = detailContext.recommendation
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

        it.codewhispererCompletionType(detailContext.completionType)
            .codewhispererGettingStartedTask(getGettingStartedTaskType(requestContext.editor))
            .codewhispererLanguage(codewhispererLanguage)
            .codewhispererPaginationProgress(numOfRecommendations)
            .codewhispererRequestId(requestId)
            .codewhispererSessionId(responseContext.sessionId)
            .codewhispererSuggestionIndex(index)
            .codewhispererSuggestionReferenceCount(recommendation.references().size)
            .codewhispererSuggestionReferences(jacksonObjectMapper().writeValueAsString(recommendation.references().map { it.licenseName() }.toSet().toList()))
            .codewhispererSuggestionImportCount(if (importEnabled) recommendation.mostRelevantMissingImports().size else null)
            .codewhispererSuggestionState(suggestionState)
            .codewhispererTriggerType(requestContext.triggerTypeInfo.triggerType)
            .credentialStartUrl(startUrl)
            .codewhispererSupplementalContextIsUtg(supplementalContext?.isUtg)
            .codewhispererSupplementalContextLength(supplementalContext?.contentLength?.toLong())
            .codewhispererSupplementalContextTimeout(supplementalContext?.isProcessTimeout)
    }

    @Deprecated(
        "Does not capture entire context of method call",
        ReplaceWith("Telemetry.codewhisperer.userTriggerDecision", "software.aws.toolkits.telemetry.Telemetry")
    )
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

        Telemetry.codewhisperer.userTriggerDecision.use {
            it.codewhispererSessionId(responseContext.sessionId)
                .codewhispererFirstRequestId(sessionContext.latencyContext.firstRequestId)
                .credentialStartUrl(getConnectionStartUrl(requestContext.connection))
                .codewhispererIsPartialAcceptance(null)
                .codewhispererPartialAcceptanceCount(null as Long?)
                .codewhispererCharactersAccepted(acceptedCharCount)
                .codewhispererCharactersRecommended(null as Long?)
                .codewhispererCompletionType(completionType)
                .codewhispererLanguage(language.toTelemetryType())
                .codewhispererTriggerType(requestContext.triggerTypeInfo.triggerType)
                .codewhispererAutomatedTriggerType(automatedTriggerType.telemetryType)
                .codewhispererLineNumber(requestContext.caretPosition.line)
                .codewhispererCursorOffset(requestContext.caretPosition.offset)
                .codewhispererSuggestionCount(recommendationContext.details.size)
                .codewhispererSuggestionImportCount(totalImportCount)
                .codewhispererTotalShownTime(popupShownTime?.toMillis()?.toDouble())
                .codewhispererTriggerCharacter(triggerChar)
                .codewhispererTypeaheadLength(recommendationContext.userInputSinceInvocation.length)
                .codewhispererTimeSinceLastDocumentChange(CodeWhispererInvocationStatus.getInstance().getTimeSinceDocumentChanged())
                .codewhispererTimeSinceLastUserDecision(codewhispererTimeSinceLastUserDecision)
                .codewhispererTimeToFirstRecommendation(sessionContext.latencyContext.paginationFirstCompletionTime)
                .codewhispererPreviousSuggestionState(previousUserTriggerDecision)
                .codewhispererSuggestionState(suggestionState)
                .codewhispererClassifierResult(classifierResult)
                .codewhispererClassifierThreshold(classifierThreshold)
                .codewhispererCustomizationArn(requestContext.customizationArn)
                .codewhispererSupplementalContextIsUtg(supplementalContext?.isUtg)
                .codewhispererSupplementalContextLength(supplementalContext?.contentLength?.toLong())
                .codewhispererSupplementalContextTimeout(supplementalContext?.isProcessTimeout)
                .codewhispererSupplementalContextStrategyId(supplementalContext?.strategy.toString())
                .codewhispererGettingStartedTask(getGettingStartedTaskType(requestContext.editor))
                .codewhispererFeatureEvaluations(CodeWhispererFeatureConfigService.getInstance().getFeatureConfigsTelemetry())
        }
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
}
