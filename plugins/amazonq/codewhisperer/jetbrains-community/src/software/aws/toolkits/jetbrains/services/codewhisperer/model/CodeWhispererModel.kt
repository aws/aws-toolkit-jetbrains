// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.model

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.Channel
import software.amazon.awssdk.services.codewhispererruntime.model.IdeDiagnostic
import software.amazon.q.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionItem
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionListWithReferences
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.PayloadContext
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManagerNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutoTriggerService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererAutomatedTriggerType
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererIntelliSenseOnHoverListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatusNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.setIntelliSensePopupAlpha
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import software.aws.toolkits.telemetry.Result
import java.util.concurrent.TimeUnit

data class Chunk(
    val content: String,
    val path: String,
    val nextChunk: String = "",
    val score: Double = 0.0,
)

data class CaretContext(val leftFileContext: String, val rightFileContext: String, val leftContextOnCurrentLine: String = "")

data class FileContextInfo(
    val caretContext: CaretContext,
    val filename: String,
    val programmingLanguage: CodeWhispererProgrammingLanguage,
    val fileRelativePath: String?,
    val fileUri: String?,
)

data class RecommendationContext(
    val details: List<DetailContext>,
    val userInput: String,
    val position: VisualPosition,
)

data class RecommendationContextNew(
    val details: MutableList<DetailContext>,
    val userInput: String,
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
    val itemId: String,
    val completion: InlineCompletionItem,
    val isDiscarded: Boolean,
    val completionType: CodewhispererCompletionType,
    var hasSeen: Boolean = false,
    var isAccepted: Boolean = false,
)

data class SessionContext(
    val typeahead: String = "",
    val typeaheadOriginal: String = "",
    val selectedIndex: Int = 0,
    val seen: MutableSet<Int> = mutableSetOf(),
    var toBeRemovedHighlighter: RangeHighlighter? = null,
    var insertEndOffset: Int = -1,
    var isPopupShowing: Boolean = false,
)

data class SessionContextNew(
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
            CodeWhispererServiceNew.CODEWHISPERER_INTELLISENSE_POPUP_ON_HOVER,
            object : CodeWhispererIntelliSenseOnHoverListener {
                override fun onEnter() {
                    CodeWhispererPopupManagerNew.getInstance().bringSuggestionInlayToFront(editor, popup, opposite = true)
                }
            }
        )
    }

    @RequiresEdt
    override fun dispose() {
        CodeWhispererTelemetryServiceNew.getInstance().sendUserTriggerDecisionEvent(this.project, this.latencyContext)
        setIntelliSensePopupAlpha(editor, 0f)
        CodeWhispererInvocationStatusNew.getInstance().setDisplaySessionActive(false)

        if (hasAccepted) {
            popup?.closeOk(null)
        } else {
            popup?.cancel()
        }
        popup?.let { Disposer.dispose(it) }
        popup = null
        CodeWhispererInvocationStatusNew.getInstance().finishInvocation()
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
    val popup: JBPopup,
) : Disposable {
    override fun dispose() {}
}

data class InvocationContextNew(
    val requestContext: RequestContextNew,
    val responseContext: ResponseContext,
    val recommendationContext: RecommendationContextNew,
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
    val completions: InlineCompletionListWithReferences,
    val popup: JBPopup,
)

data class WorkerContextNew(
    val requestContext: RequestContextNew,
    val responseContext: ResponseContext,
    val completions: InlineCompletionListWithReferences,
)

data class CodeScanTelemetryEvent(
    val codeScanResponseContext: CodeScanResponseContext,
    val duration: Double,
    val result: Result,
    val totalProjectSizeInBytes: Double?,
    val connection: ToolkitConnection?,
    val codeAnalysisScope: CodeWhispererConstants.CodeAnalysisScope,
    val initiatedByChat: Boolean = false,
)

data class CreateUploadUrlServiceInvocationContext(
    val artifactsUploadDuration: Long = 0,
    val serviceInvocationDuration: Long = 0,
)

data class CodeScanResponseContext(
    val payloadContext: PayloadContext,
    val serviceInvocationContext: CreateUploadUrlServiceInvocationContext,
    val codeScanJobId: String? = null,
    val codeScanTotalIssues: Int = 0,
    val codeScanIssuesWithFixes: Int = 0,
    val reason: String? = null,
)

data class LatencyContext(
    var perceivedLatency: Double = 0.0,

    var codewhispererEndToEndStart: Long = 0L,
    var codewhispererEndToEndEnd: Long = 0L,

    var firstRequestId: String = "",
) {
    fun getCodeWhispererEndToEndLatency() = TimeUnit.NANOSECONDS.toMillis(
        codewhispererEndToEndEnd - codewhispererEndToEndStart
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

data class InlineCompletionSessionContext(
    val itemContexts: MutableList<InlineCompletionItemContext> = mutableListOf(),
    var sessionId: String = "",
    val triggerOffset: Int,
    var counter: Int = 0,
    val diagnostics: List<IdeDiagnostic>? = emptyList(),
)

data class InlineCompletionItemContext(
    val project: Project,
    var item: InlineCompletionItem?,
    val channel: Channel<InlineCompletionElement>,
    val data: UserDataHolderBase = UserDataHolderBase(),
    var hasSeen: Boolean = false,
    var isAccepted: Boolean = false,
    var isDiscarded: Boolean = false,
    var isEmpty: Boolean = false,
)
