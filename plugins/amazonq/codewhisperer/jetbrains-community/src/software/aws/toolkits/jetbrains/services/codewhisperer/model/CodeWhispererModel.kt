// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Deferred
import software.amazon.awssdk.services.codewhispererruntime.model.Completion
import software.amazon.awssdk.services.codewhispererruntime.model.GenerateCompletionsResponse
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.services.amazonq.SUPPLEMENTAL_CONTEXT_TIMEOUT
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererIntelliSenseOnHoverListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.setIntelliSensePopupAlpha
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CrossFileStrategy
import software.aws.toolkits.jetbrains.services.codewhisperer.util.SupplementalContextStrategy
import software.aws.toolkits.jetbrains.services.codewhisperer.util.UtgStrategy
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

data class Chunk(
    val content: String,
    val path: String,
    val nextChunk: String = "",
    val score: Double = 0.0,
)

data class ListUtgCandidateResult(
    val vfile: VirtualFile?,
    val strategy: UtgStrategy,
)

data class CaretContext(val leftFileContext: String, val rightFileContext: String, val leftContextOnCurrentLine: String = "")

data class FileContextInfo(
    val caretContext: CaretContext,
    val filename: String,
    val programmingLanguage: CodeWhispererProgrammingLanguage,
    val fileRelativePath: String?,
)

data class SupplementalContextInfo(
    val isUtg: Boolean,
    val contents: List<Chunk>,
    val targetFileName: String,
    val strategy: SupplementalContextStrategy,
    val latency: Long = 0L,
) {
    val contentLength: Int
        get() = contents.fold(0) { acc, chunk ->
            acc + chunk.content.length
        }

    val isProcessTimeout: Boolean
        get() = latency > SUPPLEMENTAL_CONTEXT_TIMEOUT

    companion object {
        fun emptyCrossFileContextInfo(targetFileName: String): SupplementalContextInfo = SupplementalContextInfo(
            isUtg = false,
            contents = emptyList(),
            targetFileName = targetFileName,
            strategy = CrossFileStrategy.Empty,
            latency = 0L
        )

        fun emptyUtgFileContextInfo(targetFileName: String): SupplementalContextInfo = SupplementalContextInfo(
            isUtg = true,
            contents = emptyList(),
            targetFileName = targetFileName,
            strategy = UtgStrategy.Empty,
            latency = 0L
        )
    }
}

data class RecommendationContext(
    val details: MutableList<DetailContext>,
    val userInputOriginal: String,
    val userInputSinceInvocation: String,
    val position: VisualPosition,
    val jobId: Int,
    var typeahead: String = "",
)

data class PreviewContext(
    val jobId: Int,
    val detail: DetailContext,
    val userInput: String,
    val typeahead: String,
)

data class DetailContext(
    val requestId: String,
    val recommendation: Completion,
    val reformatted: Completion,
    val isDiscarded: Boolean,
    val isTruncatedOnRight: Boolean,
    val rightOverlap: String = "",
    val completionType: CodewhispererCompletionType,
    var hasSeen: Boolean = false,
    var isAccepted: Boolean = false,
)

data class SessionContext(
    val project: Project,
    val editor: Editor,
    var popup: JBPopup? = null,
    var selectedIndex: Int = -1,
    val seen: MutableSet<Int> = mutableSetOf(),
    var toBeRemovedHighlighter: RangeHighlighter? = null,
    var insertEndOffset: Int = -1,
    var popupOffset: Int = -1,
    val latencyContext: LatencyContext,
    var hasAccepted: Boolean = false,
) : Disposable {
    private var isDisposed = false
    init {
        project.messageBus.connect(this).subscribe(
            CodeWhispererService.CODEWHISPERER_INTELLISENSE_POPUP_ON_HOVER,
            object : CodeWhispererIntelliSenseOnHoverListener {
                override fun onEnter() {
                    CodeWhispererPopupManager.getInstance().bringSuggestionInlayToFront(editor, popup, opposite = true)
                }
            }
        )
    }

    @RequiresEdt
    override fun dispose() {
        CodeWhispererTelemetryService.getInstance().sendUserDecisionEventForAll(
            this,
            hasAccepted,
            CodeWhispererInvocationStatus.getInstance().popupStartTimestamp?.let { Duration.between(it, Instant.now()) }
        )
        setIntelliSensePopupAlpha(editor, 0f)
        CodeWhispererInvocationStatus.getInstance().setDisplaySessionActive(false)

        if (hasAccepted) {
            popup?.closeOk(null)
        } else {
            popup?.cancel()
        }
        popup?.let { Disposer.dispose(it) }
        popup = null
        CodeWhispererInvocationStatus.getInstance().finishInvocation()
        isDisposed = true
    }

    fun isDisposed() = isDisposed
}

data class RecommendationChunk(
    val text: String,
    val offset: Int,
    val inlayOffset: Int,
)

data class CaretPosition(val offset: Int, val line: Int)

data class TriggerTypeInfo(
    val triggerType: CodewhispererTriggerType,
    val automatedTriggerType: CodeWhispererAutomatedTriggerType,
)

data class InvocationContext(
    val requestContext: RequestContext,
    val responseContext: ResponseContext,
    val recommendationContext: RecommendationContext,
) : Disposable {
    private var isDisposed = false

    @RequiresEdt
    override fun dispose() {
        isDisposed = true
    }

    fun isDisposed() = isDisposed
}

data class WorkerContext(
    val requestContext: RequestContext,
    val responseContext: ResponseContext,
    val response: GenerateCompletionsResponse,
)

data class CodeScanTelemetryEvent(
    val codeScanResponseContext: CodeScanResponseContext,
    val duration: Double,
    val result: Result,
    val totalProjectSizeInBytes: Double?,
    val connection: ToolkitConnection?,
    val codeAnalysisScope: CodeWhispererConstants.CodeAnalysisScope,
)

data class CodeScanServiceInvocationContext(
    val artifactsUploadDuration: Long,
    val serviceInvocationDuration: Long,
)

data class CodeScanResponseContext(
    val payloadContext: PayloadContext,
    val serviceInvocationContext: CodeScanServiceInvocationContext,
    val codeScanJobId: String? = null,
    val codeScanTotalIssues: Int = 0,
    val codeScanIssuesWithFixes: Int = 0,
    val reason: String? = null,
)

data class LatencyContext(
    var credentialFetchingStart: Long = 0L,
    var credentialFetchingEnd: Long = 0L,

    var codewhispererPreprocessingStart: Long = 0L,
    var codewhispererPreprocessingEnd: Long = 0L,

    var paginationFirstCompletionTime: Double = 0.0,
    var perceivedLatency: Double = 0.0,

    var codewhispererPostprocessingStart: Long = 0L,
    var codewhispererPostprocessingEnd: Long = 0L,

    var codewhispererEndToEndStart: Long = 0L,
    var codewhispererEndToEndEnd: Long = 0L,

    var paginationAllCompletionsStart: Long = 0L,
    var paginationAllCompletionsEnd: Long = 0L,

    var firstRequestId: String = "",
) {
    fun getCodeWhispererEndToEndLatency() = TimeUnit.NANOSECONDS.toMillis(
        codewhispererEndToEndEnd - codewhispererEndToEndStart
    ).toDouble()

    fun getCodeWhispererAllCompletionsLatency() = TimeUnit.NANOSECONDS.toMillis(
        paginationAllCompletionsEnd - paginationAllCompletionsStart
    ).toDouble()

    fun getCodeWhispererPostprocessingLatency() = TimeUnit.NANOSECONDS.toMillis(
        codewhispererPostprocessingEnd - codewhispererPostprocessingStart
    ).toDouble()

    fun getCodeWhispererCredentialFetchingLatency() = TimeUnit.NANOSECONDS.toMillis(
        credentialFetchingEnd - credentialFetchingStart
    ).toDouble()

    fun getCodeWhispererPreprocessingLatency() = TimeUnit.NANOSECONDS.toMillis(
        codewhispererPreprocessingEnd - codewhispererPreprocessingStart
    ).toDouble()

    // For auto-trigger it's from the time when last char typed
    // for manual-trigger it's from the time when last trigger action happened(alt + c)
    fun getPerceivedLatency(triggerType: CodewhispererTriggerType) =
        if (triggerType == CodewhispererTriggerType.OnDemand) {
            getCodeWhispererEndToEndLatency()
        } else {
            TimeUnit.NANOSECONDS.toMillis(
                codewhispererEndToEndEnd - CodeWhispererAutoTriggerService.getInstance().timeAtLastCharTyped
            ).toDouble()
        }
}

data class TryExampleRowContext(
    val description: String,
    val filename: String?,
)

data class RequestContext(
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

data class ResponseContext(
    val sessionId: String,
)
