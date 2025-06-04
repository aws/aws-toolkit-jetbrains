// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.service

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class CodeWhispererInvocationStatus {
    private val isInvokingQInline: AtomicBoolean = AtomicBoolean(false)
    private var invokingSessionId: String? = null
    private var timeAtLastInvocationComplete: Instant? = null
    var timeAtLastDocumentChanged: Instant = Instant.now()
        private set
    private var isDisplaySessionActive: Boolean = false
    private var timeAtLastInvocationStart: Instant? = null
    var completionShownTime: Instant? = null
        private set
    private var currentSession: InlineCompletionSession? = null

    fun setIsInvokingQInline(session: InlineCompletionSession, value: Boolean) {
        if (!value && currentSession != session) return
        currentSession = session
        isInvokingQInline.set(value)
        // TODO: set true or false? prob doesn't matter
        ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_INVOCATION_STATE_CHANGED).invocationStateChanged(true)
    }

    fun checkExistingInvocationAndSet(): Boolean =
        if (isInvokingQInline.getAndSet(true)) {
            LOG.debug { "Have existing CodeWhisperer invocation, sessionId: $invokingSessionId" }
            true
        } else {
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_INVOCATION_STATE_CHANGED).invocationStateChanged(true)
            LOG.debug { "Starting CodeWhisperer invocation" }
            false
        }

    fun hasExistingServiceInvocation(): Boolean = isInvokingQInline.get()

    fun finishInvocation() {
        if (isInvokingQInline.compareAndSet(true, false)) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CODEWHISPERER_INVOCATION_STATE_CHANGED).invocationStateChanged(false)
            LOG.debug { "Ending CodeWhisperer invocation" }
            invokingSessionId = null
        }
    }

    fun setInvocationComplete() {
        timeAtLastInvocationComplete = Instant.now()
    }

    fun documentChanged() {
        timeAtLastDocumentChanged = Instant.now()
    }

    fun completionShown() {
        completionShownTime = Instant.now()
    }

    fun getTimeSinceDocumentChanged(): Double {
        val timeSinceDocumentChanged = Duration.between(timeAtLastDocumentChanged, Instant.now())
        val timeInDouble = timeSinceDocumentChanged.toMillis().toDouble()
        return timeInDouble
    }

    fun isDisplaySessionActive(): Boolean = isDisplaySessionActive

    fun setDisplaySessionActive(value: Boolean) {
        isDisplaySessionActive = value
    }

    fun setInvocationStart() {
        timeAtLastInvocationStart = Instant.now()
    }

    fun setInvocationSessionId(sessionId: String?) {
        LOG.debug { "Set current CodeWhisperer invocation sessionId: $sessionId" }
        invokingSessionId = sessionId
    }

    companion object {
        private val LOG = getLogger<CodeWhispererInvocationStatus>()
        fun getInstance(): CodeWhispererInvocationStatus = service()
        val CODEWHISPERER_INVOCATION_STATE_CHANGED: Topic<CodeWhispererInvocationStateChangeListener> = Topic.create(
            "CodeWhisperer popup state changed",
            CodeWhispererInvocationStateChangeListener::class.java
        )
    }
}

interface CodeWhispererInvocationStateChangeListener {
    fun invocationStateChanged(value: Boolean) {}
}
