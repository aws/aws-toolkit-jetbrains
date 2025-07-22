// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.sono.isInternalUser
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.InlineCompletionStates
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LogInlineCompletionSessionResultsParams
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InlineCompletionSessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.RecommendationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.QInlineCompletionProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCodeWhispererStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.DiagnosticDifferences
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getDiagnosticDifferences
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getDocumentDiagnostics
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.telemetry.CodeFixAction
import software.aws.toolkits.telemetry.CodewhispererCodeScanScope
import software.aws.toolkits.telemetry.CodewhispererGettingStartedTask
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.CredentialSourceId
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant

@Service
class CodeWhispererTelemetryService(private val cs: CoroutineScope) {
    companion object {
        fun getInstance(): CodeWhispererTelemetryService = service()
        val LOG = getLogger<CodeWhispererTelemetryService>()
    }

    fun sendUserTriggerDecisionEvent(
        project: Project,
        latencyContext: LatencyContext,
        sessionId: String,
        recommendationContext: RecommendationContext,
    ) {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { server ->
                val params = LogInlineCompletionSessionResultsParams(
                    sessionId = sessionId,
                    completionSessionResult = recommendationContext.details.associate {
                        it.itemId to InlineCompletionStates(
                            seen = it.hasSeen,
                            accepted = it.isAccepted,
                            discarded = it.isDiscarded
                        )
                    },
                    firstCompletionDisplayLatency = latencyContext.perceivedLatency,
                    totalSessionDisplayTime = CodeWhispererInvocationStatus.getInstance().completionShownTime?.let { Duration.between(it, Instant.now()) }
                        ?.toMillis()?.toDouble(),
                    typeaheadLength = recommendationContext.userInput.length.toLong()
                )
                server.logInlineCompletionSessionResults(params)
            }
        }
    }

    suspend fun sendUserTriggerDecisionEventForTriggerSession(
        project: Project,
        latencyContext: LatencyContext,
        sessionContext: InlineCompletionSessionContext,
        triggerSessionId: Int,
        document: Document,
    ) {
        if (sessionContext.sessionId.isEmpty()) {
            QInlineCompletionProvider.logInline(triggerSessionId) {
                "Did not receive a valid sessionId from language server, skipping telemetry"
            }
            return
        }
        QInlineCompletionProvider.logInline(triggerSessionId) {
            "Sending UserTriggerDecision for ${sessionContext.sessionId}:"
        }
        sessionContext.itemContexts.forEachIndexed { i, itemContext ->
            QInlineCompletionProvider.logInline(triggerSessionId) {
                "Index: $i, item: ${itemContext.item?.itemId}, seen: ${itemContext.hasSeen}, " +
                    "accepted: ${itemContext.isAccepted}, discarded: ${itemContext.isDiscarded}"
            }
        }
        QInlineCompletionProvider.logInline(triggerSessionId) {
            "Perceived latency: ${latencyContext.perceivedLatency}, " +
                "total session display time: ${CodeWhispererInvocationStatus.getInstance().completionShownTime?.let { Duration.between(it, Instant.now()) }
                    ?.toMillis()?.toDouble()}"
        }
        var diffDiagnostics = DiagnosticDifferences(
            added = emptyList(),
            removed = emptyList()
        )

        if (isInternalUser(getStartUrl(project))) {
            val oldDiagnostics = sessionContext.diagnostics.orEmpty()
            // wait for the IDE itself to update its diagnostics for current file
            delay(500)
            val newDiagnostics = getDocumentDiagnostics(document, project)
            diffDiagnostics = getDiagnosticDifferences(oldDiagnostics, newDiagnostics)
        }
        val params = LogInlineCompletionSessionResultsParams(
            sessionId = sessionContext.sessionId,
            completionSessionResult = sessionContext.itemContexts.filter { it.item != null }.associate {
                it.item?.itemId.orEmpty() to InlineCompletionStates(
                    seen = it.hasSeen,
                    accepted = it.isAccepted,
                    discarded = it.isDiscarded
                )
            },
            firstCompletionDisplayLatency = latencyContext.perceivedLatency,
            totalSessionDisplayTime = CodeWhispererInvocationStatus.getInstance().completionShownTime?.let { Duration.between(it, Instant.now()) }
                ?.toMillis()?.toDouble(),
            // no userInput in JB inline completion API, every new char input will discard the previous trigger so
            // user input is always 0
            typeaheadLength = 0,
            addedDiagnostics = diffDiagnostics.added,
            removedDiagnostics = diffDiagnostics.removed,
        )
        AmazonQLspService.executeAsyncIfRunning(project) { server ->
            server.logInlineCompletionSessionResults(params)
        }
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

    fun sendOnboardingClickEvent(language: CodeWhispererProgrammingLanguage, taskType: CodewhispererGettingStartedTask) {
        // Project instance is not needed. We look at these metrics for each clientId.
        CodewhispererTelemetry.onboardingClick(project = null, codewhispererLanguage = language.toTelemetryType(), codewhispererGettingStartedTask = taskType)
    }
}

fun isTelemetryEnabled(): Boolean = AwsSettings.getInstance().isTelemetryEnabled
