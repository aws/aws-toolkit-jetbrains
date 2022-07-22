// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import org.jetbrains.annotations.TestOnly
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_SECONDS_IN_MINUTE
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
    totalTokensSize: AtomicInteger,
    rangeMarker: RangeMarker? = null
) : Disposable {
    val percentage: Int
        get() = if (totalTokensSize.get() != 0) (acceptedTokensSize.get().toDouble() / totalTokensSize.get() * 100).roundToInt() else 0
    var acceptedTokensSize = acceptedTokensSize
        private set
    var totalTokensSize = totalTokensSize
        private set
    var myRangeMarker: RangeMarker? = rangeMarker
        private set
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val isShuttingDown = AtomicBoolean(false)
    private var startTime: Instant = Instant.now()

    init {
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(
            CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, remainingRecomm: String, rangeMarker: RangeMarker?) {
                    if (states.requestContext.fileContextInfo.programmingLanguage.toCodeWhispererLanguage() != language) return
                    myRangeMarker = rangeMarker
                    addAndGetAcceptedTokens(remainingRecomm.length)
                }
            }
        )
        scheduleCodeWhispererCodeCoverageTracker()
    }

    fun documentChanged(event: DocumentEvent) {
        addAndGetTotalTokens(event.newLength - event.oldLength)
    }

    private fun flush() {
        try {
            if (isTelemetryEnabled()) emitCodeWhispererCodeContribution()
        } finally {
            init()
            scheduleCodeWhispererCodeCoverageTracker()
        }
    }

    private fun scheduleCodeWhispererCodeCoverageTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, Duration.ofSeconds(timeWindowInSec).toMillis())
        }
    }

    private fun addAndGetAcceptedTokens(delta: Int): Int =
        if (!isTelemetryEnabled()) acceptedTokensSize.get()
        else acceptedTokensSize.addAndGet(delta)

    private fun addAndGetTotalTokens(delta: Int): Int =
        if (!isTelemetryEnabled()) totalTokensSize.get()
        else {
            val result = totalTokensSize.addAndGet(delta)
            if (result < 0) totalTokensSize.set(0)
            result
        }

    private fun init() {
        startTime = Instant.now()
        totalTokensSize.set(0)
        acceptedTokensSize.set(0)
        myRangeMarker = null
    }

    private fun emitCodeWhispererCodeContribution() {
        myRangeMarker?.let {
            if (!it.isValid) {
                acceptedTokensSize.set(0)
            } else {
                acceptedTokensSize.set(it.endOffset - it.startOffset)
            }
        }
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
        private val instances: MutableMap<CodewhispererLanguage, CodeWhispererCodeCoverageTracker> = mutableMapOf()

        fun getInstance(language: CodewhispererLanguage): CodeWhispererCodeCoverageTracker = when (val instance = instances[language]) {
            null -> {
                val newTracker = DefaultCodeWhispererCodeCoverageTracker(language)
                instances[language] = newTracker
                newTracker
            }
            else -> instance
        }

        @TestOnly
        fun getInstancesMap(): MutableMap<CodewhispererLanguage, CodeWhispererCodeCoverageTracker> {
            assert(ApplicationManager.getApplication().isUnitTestMode)
            return instances
        }
    }
}

class DefaultCodeWhispererCodeCoverageTracker(language: CodewhispererLanguage) : CodeWhispererCodeCoverageTracker(
    5 * TOTAL_SECONDS_IN_MINUTE,
    language,
    acceptedTokensSize = AtomicInteger(0),
    totalTokensSize = AtomicInteger(0)
)
