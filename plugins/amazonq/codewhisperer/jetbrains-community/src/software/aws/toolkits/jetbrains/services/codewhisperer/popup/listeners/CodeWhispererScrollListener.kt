// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatusNew

class CodeWhispererScrollListener(private val states: InvocationContext) : VisibleAreaListener {
    override fun visibleAreaChanged(e: VisibleAreaEvent) {
        val oldRect = e.oldRectangle
        val newRect = e.newRectangle
        if (CodeWhispererInvocationStatus.getInstance().isDisplaySessionActive() &&
            (oldRect.x != newRect.x || oldRect.y != newRect.y)
        ) {
            ApplicationManager.getApplication().messageBus.syncPublisher(
                CodeWhispererPopupManager.CODEWHISPERER_POPUP_STATE_CHANGED
            ).scrolled(states, CodeWhispererPopupManager.getInstance().sessionContext)
        }
    }
}

class CodeWhispererScrollListenerNew(private val sessionContext: SessionContextNew) : VisibleAreaListener {
    override fun visibleAreaChanged(e: VisibleAreaEvent) {
        val oldRect = e.oldRectangle
        val newRect = e.newRectangle
        if (CodeWhispererInvocationStatusNew.getInstance().isDisplaySessionActive() &&
            (oldRect.x != newRect.x || oldRect.y != newRect.y)
        ) {
            ApplicationManager.getApplication().messageBus.syncPublisher(
                CodeWhispererPopupManager.CODEWHISPERER_POPUP_STATE_CHANGED
            ).scrolled(sessionContext)
        }
    }
}
