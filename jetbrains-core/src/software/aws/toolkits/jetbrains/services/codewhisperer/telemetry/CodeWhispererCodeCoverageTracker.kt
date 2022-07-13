// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererDocumentChangedListener
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorListener
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

abstract class CodeWhispererCodeCoverageTracker(private val project: Project, protected val timeWindowInSec: Long): Disposable {
    private val alarm: Alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val acceptedToken: StringBuilder = StringBuilder()
    private val totalTokens: StringBuilder = StringBuilder()
    private val isShuttingDown = AtomicBoolean(false)
    private var language: CodewhispererLanguage = CodewhispererLanguage.Unknown
    private var startTime: Instant = Instant.now()

    init {
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(
            CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED,
            object: CodeWhispererUserActionListener {
                override fun beforeAccept(states: InvocationContext, sessionContext: SessionContext) {
                    val (_, _, recommendationContext) = states
                    val selectedIndex = sessionContext.selectedIndex
                    val typeahead = sessionContext.typeahead
                    val reformatted = CodeWhispererPopupManager.getInstance().getReformattedRecommendation(
                        recommendationContext.details[selectedIndex], recommendationContext.userInputSinceInvocation
                    )
                    var remainingRecommendation = reformatted.substring(typeahead.length)
                    language = states.requestContext.fileContextInfo.programmingLanguage.toCodeWhispererLanguage()

                    pushAcceptedTokens(remainingRecommendation)
                }
            }
        )
        conn.subscribe(
            CodeWhispererEditorListener.CODEWHISPERER_DOCUMENT_CHANGE,
            object: CodeWhispererDocumentChangedListener {
                override fun documentChanged(event: DocumentEvent) {
                    pushTotalTokens(event.newFragment)
                }
            }
        )

        scheduleCodeWhispererTracker()
    }

    private fun scheduleCodeWhispererTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, Duration.ofSeconds(timeWindowInSec).toMillis())
        }
    }

    fun pushAcceptedTokens(chars: CharSequence) {
        if (!isTelemetryEnabled()) return
        acceptedToken.append(chars)
    }

    fun pushTotalTokens(chars: CharSequence) {
        if (!isTelemetryEnabled()) return
        totalTokens.append(chars)
    }

    private fun flush() {
        try {
            if (!isTelemetryEnabled()) {
                init()
                return
            }
            emitCodeWhispererCodeContribution()
        } finally {
            init()
            scheduleCodeWhispererTracker()
        }
    }

    private fun init() {
        acceptedToken.clear()
        totalTokens.clear()
        startTime = Instant.now()
    }

    private fun emitCodeWhispererCodeContribution() {
        val acceptedTokenSize = acceptedToken.length
        val totalTokensSize = totalTokens.length
        val percentage = if (totalTokensSize != 0) (acceptedTokenSize.toDouble() / totalTokensSize * 100).roundToInt() else 0
        // TODO
        println("accpetedTokens: $acceptedToken")
        println("totalTokens: $totalTokens")
        println("acceptedTokenSize = $acceptedTokenSize, totalTokenSize = $totalTokensSize, percentage = $percentage")
        CodewhispererTelemetry.codePercentage(
            project = project,
            acceptedTokenSize,
            language,
            percentage,
            startTime.toString(),
            totalTokensSize
        )
    }

    override fun dispose() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }
        flush()
    }

    companion object {
        private val logger = getLogger<CodeWhispererCodeCoverageTracker>()
        fun getInstance(project: Project) = project.service<CodeWhispererCodeCoverageTracker>()
    }
}

class DefaultCodeWhispererCodeCoverageTracker(project: Project): CodeWhispererCodeCoverageTracker(project, 300) {}
