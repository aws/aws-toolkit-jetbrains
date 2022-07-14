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

abstract class CodeWhispererCodeCoverageTracker(
    private val project: Project,
    private val timeWindowInSec: Long,
    private val acceptedTokensBuffer: StringBuilder,
    private val totalTokensBuffer: StringBuilder
) : Disposable {
    val acceptedTokens: String
        get() = acceptedTokensBuffer.toString()
    val totalTokens: String
        get() = totalTokensBuffer.toString()
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val isShuttingDown = AtomicBoolean(false)
    private var language: CodewhispererLanguage = CodewhispererLanguage.Unknown
    private var startTime: Instant = Instant.now()

    init {
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(
            CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, remainingRecomm: String) {
                    pushAcceptedTokens(remainingRecomm)
                }
            }
        )
        conn.subscribe(
            CodeWhispererEditorListener.CODEWHISPERER_DOCUMENT_CHANGE,
            object : CodeWhispererDocumentChangedListener {
                override fun documentChanged(event: DocumentEvent) {
                    pushTotalTokens(event.newFragment)
                }
            }
        )
        scheduleCodeWhispererTracker()
    }

    fun flush() {
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

    internal fun scheduleCodeWhispererTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, Duration.ofSeconds(timeWindowInSec).toMillis())
        }
    }

    private fun pushAcceptedTokens(chars: CharSequence) {
        if (!isTelemetryEnabled()) return
        acceptedTokensBuffer.append(chars)
    }

    private fun pushTotalTokens(chars: CharSequence) {
        if (!isTelemetryEnabled()) return
        totalTokensBuffer.append(chars)
    }

    private fun init() {
        acceptedTokensBuffer.clear()
        totalTokensBuffer.clear()
        startTime = Instant.now()
    }

    private fun emitCodeWhispererCodeContribution() {
        val acceptedTokenSize = acceptedTokensBuffer.length
        val totalTokensSize = totalTokensBuffer.length
        val percentage = if (totalTokensSize != 0) (acceptedTokenSize.toDouble() / totalTokensSize * 100).roundToInt() else 0
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

class DefaultCodeWhispererCodeCoverageTracker(project: Project) :
    CodeWhispererCodeCoverageTracker(
        project,
        300,
        StringBuilder(),
        StringBuilder()
    )
