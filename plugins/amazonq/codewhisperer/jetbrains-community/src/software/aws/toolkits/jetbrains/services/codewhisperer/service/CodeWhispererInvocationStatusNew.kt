// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus.Companion.CODEWHISPERER_INVOCATION_STATE_CHANGED
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class CodeWhispererInvocationStatusNew {
    private val isInvokingService: AtomicBoolean = AtomicBoolean(false)
    private var invokingSessionId: String? = null
    var timeAtLastDocumentChanged: Instant = Instant.now()
        private set
    private var isPopupActive: Boolean = false
    private var timeAtLastInvocationStart: Instant? = null
    var popupStartTimestamp: Instant? = null
        private set

    fun startInvocation() {
        isInvokingService.set(true)
        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_INVOCATION_STATE_CHANGED).invocationStateChanged(true)
        LOG.debug { "Starting CodeWhisperer invocation" }
    }

    fun hasExistingServiceInvocation(): Boolean = isInvokingService.get()

    fun finishInvocation() {
        if (isInvokingService.compareAndSet(true, false)) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_INVOCATION_STATE_CHANGED).invocationStateChanged(false)
            LOG.debug { "Ending CodeWhisperer invocation" }
            invokingSessionId = null
        }
    }

    fun documentChanged() {
        timeAtLastDocumentChanged = Instant.now()
    }

    fun setPopupStartTimestamp() {
        popupStartTimestamp = Instant.now()
    }

    fun getTimeSinceDocumentChanged(): Double {
        val timeSinceDocumentChanged = Duration.between(timeAtLastDocumentChanged, Instant.now())
        val timeInDouble = timeSinceDocumentChanged.toMillis().toDouble()
        return timeInDouble
    }

    fun hasEnoughDelayToShowCodeWhisperer(): Boolean {
        val timeCanShowCodeWhisperer = timeAtLastDocumentChanged.plusMillis(50)
        return timeCanShowCodeWhisperer.isBefore(Instant.now())
    }

    fun isDisplaySessionActive(): Boolean = isPopupActive

    fun setDisplaySessionActive(value: Boolean) {
        isPopupActive = value
    }

    fun setInvocationStart() {
        timeAtLastInvocationStart = Instant.now()
    }

    fun setInvocationSessionId(sessionId: String?) {
        LOG.debug { "Set current CodeWhisperer invocation sessionId: $sessionId" }
        invokingSessionId = sessionId
    }

    companion object {
        private val LOG = getLogger<CodeWhispererInvocationStatusNew>()
        fun getInstance(): CodeWhispererInvocationStatusNew = service()
    }
}
