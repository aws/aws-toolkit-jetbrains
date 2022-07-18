// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

abstract class CodeWhispererCodeCoverageTracker(
    private val timeWindowInSec: Long,
    private val language: CodewhispererLanguage,
    acceptedTokensSize: AtomicInteger,
    totalTokensSize: AtomicInteger
) : Disposable {
    val percentage: Int
        get() = if (totalTokensSize.get() != 0) (acceptedTokensSize.get().toDouble() / totalTokensSize.get() * 100).roundToInt() else 0
    var acceptedTokensSize = acceptedTokensSize
        private set
    var totalTokensSize = totalTokensSize
        private set
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val isShuttingDown = AtomicBoolean(false)
    private var startTime: Instant = Instant.now()

    init {
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(
            CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, remainingRecomm: String) {
                    if (states.requestContext.fileContextInfo.programmingLanguage.toCodeWhispererLanguage() != language) return
                    pushAcceptedTokens(remainingRecomm)
                }
            }
        )
        scheduleCodeWhispererTracker()
    }

    fun documentChanged(event: DocumentEvent) {
        pushTotalTokens(event.newFragment)
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

    private fun scheduleCodeWhispererTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, Duration.ofSeconds(timeWindowInSec).toMillis())
        }
    }

    private fun pushAcceptedTokens(chars: CharSequence) {
        if (!isTelemetryEnabled()) return
        acceptedTokensSize.addAndGet(chars.length)
    }

    private fun pushTotalTokens(chars: CharSequence) {
        if (!isTelemetryEnabled()) return
        totalTokensSize.addAndGet(chars.length)
    }

    private fun init() {
        startTime = Instant.now()
        totalTokensSize.set(0)
        acceptedTokensSize.set(0)
    }

    private fun emitCodeWhispererCodeContribution() {
        CodewhispererTelemetry.codePercentage(
            project = null,
            acceptedTokensSize.get(),
            language,
            percentage,
            startTime.toString(),
            totalTokensSize.get()
        )
    }

    @TestOnly
    fun forceTrackerFlush() {
        alarm.drainRequestsInTest()
    }

    @TestOnly
    fun activeRequestCount() = alarm.activeRequestCount

    override fun dispose() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }
        flush()
    }

    companion object {
        const val FIVE_MINS_IN_SECS = 300L
        internal val instances: MutableMap<CodewhispererLanguage, CodeWhispererCodeCoverageTracker> = mutableMapOf()

        fun getInstance(language: CodewhispererLanguage): CodeWhispererCodeCoverageTracker = if (instances.containsKey(language)) {
            instances[language] ?: throw Exception("Shouldn't be here")
        } else {
            val newTracker = DefaultCodeWhispererCodeCoverageTracker(language)
            instances[language] = newTracker
            newTracker
        }
    }
}

class DefaultCodeWhispererCodeCoverageTracker(language: CodewhispererLanguage) : CodeWhispererCodeCoverageTracker(
    FIVE_MINS_IN_SECS,
    language,
    acceptedTokensSize = AtomicInteger(0),
    totalTokensSize = AtomicInteger(0)
)
