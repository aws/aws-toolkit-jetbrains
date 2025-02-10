// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.notification.NotificationType
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import java.util.concurrent.CompletableFuture

/**
 * Concrete implementation of [AmazonQLanguageClient] to handle messages sent from server
 */
class AmazonQLanguageClientImpl : AmazonQLanguageClient {
    override fun telemetryEvent(`object`: Any) {
        println(`object`)
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        println(diagnostics)
    }

    override fun showMessage(messageParams: MessageParams) {
        val type = when (messageParams.type) {
            MessageType.Error -> NotificationType.ERROR
            MessageType.Warning -> NotificationType.WARNING
            MessageType.Info, MessageType.Log -> NotificationType.INFORMATION
        }
        println("$type: ${messageParams.message}")
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem?>? {
        println(requestParams)

        return CompletableFuture.completedFuture(null)
    }

    override fun logMessage(message: MessageParams) {
        showMessage(message)
    }

    override fun getConnectionMetadata() = CompletableFuture.completedFuture(
        ConnectionMetadata(
            SsoProfileData("TODO")
        )
    )

    override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> {
        if (params.items.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }

        return CompletableFuture.completedFuture(
            buildList {
            }
        )
    }
}
