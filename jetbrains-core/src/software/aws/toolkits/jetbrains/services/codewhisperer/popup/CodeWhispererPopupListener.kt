// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class CodeWhispererPopupListener(private val states: InvocationContext) : JBPopupListener {
    private val popupDuration: Duration?
        get() {
            return CodeWhispererInvocationStatus.getInstance().popupStartTimestamp?.let {
                val result = Duration.between(it, Instant.now())
                return result
            }
        }
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
            CodeWhispererPopupManager.getInstance().sessionContext,
            event.isOk,
            popupDuration
        )

        println(popupDuration?.toMillis())

        CodeWhispererInvocationStatus.getInstance().setPopupActive(false)
    }
}
