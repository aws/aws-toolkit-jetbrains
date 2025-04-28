// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup

import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import io.ktor.client.request.request
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.InlineCompletionStates
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LogInlineCompletionSessionResultsParams
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererInvocationStatus
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import java.time.Duration
import java.time.Instant

class CodeWhispererPopupListener(private val states: InvocationContext) : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
        super.beforeShown(event)
        CodeWhispererInvocationStatus.getInstance().completionShown()
    }
    override fun onClosed(event: LightweightWindowEvent) {
        super.onClosed(event)
        val (requestContext, responseContext, recommendationContext) = states

        CodeWhispererTelemetryService.getInstance().sendUserTriggerDecisionEvent(
            requestContext.project,
            requestContext.latencyContext,
            responseContext.sessionId,
            recommendationContext
        )
        CodeWhispererInvocationStatus.getInstance().setDisplaySessionActive(false)
    }
}

class CodeWhispererPopupListenerNew : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
        super.onClosed(event)
        CodeWhispererServiceNew.getInstance().disposeDisplaySession(event.isOk)
    }
}
