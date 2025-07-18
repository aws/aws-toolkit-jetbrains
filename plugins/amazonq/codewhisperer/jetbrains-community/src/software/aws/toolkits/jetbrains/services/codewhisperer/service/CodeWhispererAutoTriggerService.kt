// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import kotlinx.coroutines.Job
import org.apache.commons.collections4.queue.CircularFifoQueue
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.telemetry.CodewhispererPreviousSuggestionState
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.time.Duration
import java.time.Instant

@Service
class CodeWhispererAutoTriggerService : CodeWhispererAutoTriggerHandler, Disposable {
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val previousUserTriggerDecisions = CircularFifoQueue<CodewhispererPreviousSuggestionState>(5)

    private var lastInvocationTime: Instant? = null
    private var lastInvocationLineNum: Int? = null
    var timeAtLastCharTyped: Long = System.nanoTime()
        private set

    init {
        scheduleReset()
    }

    // real auto trigger logic
    fun invoke(editor: Editor): Job? {
        timeAtLastCharTyped = System.nanoTime()
        if (!(
                if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
                    CodeWhispererServiceNew.getInstance().canDoInvocation(editor, CodewhispererTriggerType.AutoTrigger)
                } else {
                    CodeWhispererService.getInstance().canDoInvocation(editor, CodewhispererTriggerType.AutoTrigger)
                }
                )
        ) {
            return null
        }

        lastInvocationTime = Instant.now()
        lastInvocationLineNum = runReadAction { editor.caretModel.visualPosition.line }

        return null
    }

    private fun scheduleReset() {
        if (!alarm.isDisposed) {
            alarm.addRequest({ resetPreviousStates() }, Duration.ofSeconds(120).toMillis())
        }
    }

    private fun resetPreviousStates() {
        try {
            previousUserTriggerDecisions.clear()
            lastInvocationLineNum = null
            lastInvocationTime = null
        } finally {
            scheduleReset()
        }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): CodeWhispererAutoTriggerService = service()
    }
}
