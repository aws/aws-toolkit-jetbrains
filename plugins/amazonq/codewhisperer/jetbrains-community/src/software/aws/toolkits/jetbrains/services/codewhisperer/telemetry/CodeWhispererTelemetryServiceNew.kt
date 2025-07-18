// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.InlineCompletionStates
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LogInlineCompletionSessionResultsParams
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.model.LatencyContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCodeWhispererStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.telemetry.CodewhispererCodeScanScope
import software.aws.toolkits.telemetry.CodewhispererGettingStartedTask
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.Component
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant

@Service
class CodeWhispererTelemetryServiceNew(private val cs: CoroutineScope) {

    companion object {
        fun getInstance(): CodeWhispererTelemetryServiceNew = service()
        val LOG = getLogger<CodeWhispererTelemetryServiceNew>()
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

    fun sendUserTriggerDecisionEvent(project: Project, latencyContext: LatencyContext) {
        cs.launch {
            AmazonQLspService.executeAsyncIfRunning(project) { server ->
                CodeWhispererServiceNew.getInstance().getAllPaginationSessions().forEach { jobId, state ->
                    if (state == null) return@forEach
                    val params = LogInlineCompletionSessionResultsParams(
                        sessionId = state.responseContext.sessionId,
                        completionSessionResult = state.recommendationContext.details.associate {
                            it.itemId to InlineCompletionStates(
                                seen = it.hasSeen,
                                accepted = it.isAccepted,
                                discarded = it.isDiscarded
                            )
                        },
                        firstCompletionDisplayLatency = latencyContext.perceivedLatency,
                        totalSessionDisplayTime = CodeWhispererInvocationStatus.getInstance().completionShownTime?.let { Duration.between(it, Instant.now()) }
                            ?.toMillis()?.toDouble(),
                        typeaheadLength = state.recommendationContext.userInput.length.toLong()
                    )
                    server.logInlineCompletionSessionResults(params)
                }
            }
        }
    }

    fun sendOnboardingClickEvent(language: CodeWhispererProgrammingLanguage, taskType: CodewhispererGettingStartedTask) {
        // Project instance is not needed. We look at these metrics for each clientId.
        CodewhispererTelemetry.onboardingClick(project = null, codewhispererLanguage = language.toTelemetryType(), codewhispererGettingStartedTask = taskType)
    }
}
