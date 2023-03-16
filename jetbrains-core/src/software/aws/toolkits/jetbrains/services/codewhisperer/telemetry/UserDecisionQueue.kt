// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil
import software.aws.toolkits.telemetry.CodewhispererPreviousSuggestionState
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import java.time.Duration
import java.time.Instant

class CodeWhispererUserDecisionQueue(val size: Int = 5) {
    private val previousN = mutableListOf<TriggerLevelUserDecision>()
    private val recoLevelDecisionBuffer: MutableMap<String, MutableList<RecoLevelUserDecision>> = mutableMapOf()
    private var previousUserTriggerDecisionTimestamp: Instant? = null

    private val codewhispererTimeSinceLastUserDecision: Double?
        get() {
            return previousUserTriggerDecisionTimestamp?.let {
                Duration.between(it, Instant.now()).toMillis().toDouble()
            } ?: run {
                null
            }
        }

    private val codewhispererPreviousSuggestionState: CodewhispererPreviousSuggestionState?
        get() {
            return mostRecentDecision()?.decision?.let {
                CodewhispererPreviousSuggestionState.from(it.name)
            } ?: null
        }

    fun resetClassifierStates() {
        previousN.clear()
        recoLevelDecisionBuffer.clear()
    }

    fun push(recoLevelUserDecision: RecoLevelUserDecision, isLastDecision: Boolean) {
        val sessionId = recoLevelUserDecision.responseContext.sessionId

        recoLevelDecisionBuffer[sessionId]?.add(recoLevelUserDecision) ?: run {
            // first decision in the session
            recoLevelDecisionBuffer[sessionId] = mutableListOf(recoLevelUserDecision)
        }

        assert(recoLevelDecisionBuffer.size == 1)
        if (recoLevelDecisionBuffer.size != 1) {
            LOG.debug { "recoLevelDecisionBuffer size is not 0, logical error" }
        }

        if (isLastDecision) {
            flush()
        }
    }

    fun mostRecentDecision(): TriggerLevelUserDecision? = if (previousN.isNotEmpty()) previousN.last() else null

    fun mostRecentlyNDecisions(): List<TriggerLevelUserDecision> = previousN.toList()

    private fun flush() {
        if (recoLevelDecisionBuffer.size != 1) {
            LOG.debug { "$recoLevelDecisionBuffer" }
            return
        }

        // theoretically the size of this buffer is always 1 thus forEach only loop 1 element
        recoLevelDecisionBuffer.forEach { (sessionId, decisions) ->
            this.previousN.add(aggregateRecoLevelDecision(decisions.toList()))

            if (this.previousN.size > this.size) {
                this.previousN.removeFirstOrNull()
            }
        }

        recoLevelDecisionBuffer.clear()
    }

    private fun aggregateRecoLevelDecisionHelper(decisions: List<RecoLevelUserDecision>): TriggerLevelUserDecision {
        var isEmpty = true

        for ((_, recoLevelDecision) in decisions.withIndex()) {
            val sessionid = recoLevelDecision.responseContext.sessionId
            val decision = recoLevelDecision.decision
            if (decision == CodewhispererSuggestionState.Accept) {
                return TriggerLevelUserDecision(sessionid, decision)
            } else if (decision == CodewhispererSuggestionState.Reject) {
                return TriggerLevelUserDecision(sessionid, decision)
            } else if (decision == CodewhispererSuggestionState.Empty) {
                isEmpty = false
            }
        }

        // TODO: should return CodewhispererSuggestionState.Other or something
        return if (isEmpty) TriggerLevelUserDecision(
            decisions[0].responseContext.sessionId,
            CodewhispererSuggestionState.Empty
        ) else TriggerLevelUserDecision(
            decisions[0].responseContext.sessionId,
            CodewhispererSuggestionState.Discard
        )
    }

    private fun aggregateRecoLevelDecision(decisions: List<RecoLevelUserDecision>): TriggerLevelUserDecision {
        val triggerLevelDecision = aggregateRecoLevelDecisionHelper(decisions)

        val firstRequestContext = decisions.first().requestContext
        val firstResponseContext = decisions.first().responseContext
        val firstRecommendationContext = decisions.first().recommendationContext
        val duration = decisions.first().popupShownTime

        CodewhispererTelemetry.userTriggerDecision(
            project = firstRequestContext.project,
            codewhispererSessionId = firstResponseContext.sessionId,
            codewhispererFirstRequestId = firstRequestContext.latencyContext.firstRequestId,
            credentialStartUrl = CodeWhispererUtil.getConnectionStartUrl(firstRequestContext.connection),
            null,
            null,
            null,
            null,
            codewhispererCompletionType = firstResponseContext.completionType,
            codewhispererLanguage = firstRequestContext.fileContextInfo.programmingLanguage.toTelemetryType(),
            codewhispererTriggerType = firstRequestContext.triggerTypeInfo.triggerType,
            codewhispererAutomatedTriggerType = firstRequestContext.triggerTypeInfo.automatedTriggerType,
            codewhispererLineNumber = firstRequestContext.caretPosition.line,
            codewhispererCursorOffset = firstRequestContext.caretPosition.offset,
            codewhispererSuggestionCount = decisions.size,
            codewhispererSuggestionImportCount = 0, // TODO
            codewhispererTotalShownTime = duration?.toMillis()?.toDouble(),
            codewhispererTriggerCharacter = "", // TODO
            codewhispererTypeaheadLength = firstRecommendationContext.userInputSinceInvocation.length,
            codewhispererTimeSinceLastDocumentChange = null, // TODO
            codewhispererTimeSinceLastUserDecision = codewhispererTimeSinceLastUserDecision,
            codewhispererTimeToFirstRecommendation = firstRequestContext.latencyContext.paginationFirstCompletionTime,
            codewhispererPreviousSuggestionState = codewhispererPreviousSuggestionState,
        )

        previousUserTriggerDecisionTimestamp = Instant.now()

        return triggerLevelDecision
    }

    companion object {
        private val LOG = getLogger<CodeWhispererUserDecisionQueue>()
    }
}

data class RecoLevelUserDecision(
    val requestContext: RequestContext,
    val responseContext: ResponseContext,
    val recommendationContext: RecommendationContext,
    val credentialStartUrl: String?,
    val decision: CodewhispererSuggestionState,
    val index: Int,
    val popupShownTime: Duration?
)

data class TriggerLevelUserDecision(
    val sessionId: String,
    val decision: CodewhispererSuggestionState
)
