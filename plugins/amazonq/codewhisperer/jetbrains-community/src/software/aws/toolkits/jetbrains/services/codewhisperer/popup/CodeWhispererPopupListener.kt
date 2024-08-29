// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import java.time.Duration
import java.time.Instant

class CodeWhispererPopupListener(private val states: InvocationContext, private val sessionContext: SessionContext) : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
        super.beforeShown(event)
        CodeWhispererInvocationStatus.getInstance().setPopupStartTimestamp()
    }
    override fun onClosed(event: LightweightWindowEvent) {
        super.onClosed(event)
        val (requestContext, responseContext, recommendationContext) = states

        CodeWhispererTelemetryService.getInstance().sendUserDecisionEventForAll(
            requestContext,
            responseContext,
            recommendationContext,
            sessionContext,
            event.isOk,
            CodeWhispererInvocationStatus.getInstance().popupStartTimestamp?.let { Duration.between(it, Instant.now()) }
        )

        CodeWhispererInvocationStatus.getInstance().setPopupActive(false)
        Disposer.dispose(sessionContext)
    }
}
