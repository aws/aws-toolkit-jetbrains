// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import software.amazon.awssdk.services.toolkittelemetry.model.MetricUnit
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.TelemetryParsingUtil
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Concrete implementation of [AmazonQLanguageClient] to handle messages sent from server
 */
class AmazonQLanguageClientImpl(private val project: Project) : AmazonQLanguageClient {

    private fun handleTelemetryMap(telemetryMap: Map<*, *>) {
        try {
            val name = telemetryMap["name"] as? String ?: return

            @Suppress("UNCHECKED_CAST")
            val data = telemetryMap["data"] as? Map<String, Any> ?: return

            TelemetryService.getInstance().record(project) {
                datum(name) {
                    createTime(Instant.now())
                    unit(TelemetryParsingUtil.parseMetricUnit(telemetryMap["unit"]))
                    value(telemetryMap["value"] as? Double ?: 1.0)
                    passive(telemetryMap["passive"] as? Boolean ?: false)

                    telemetryMap["result"]?.let { result ->
                        metadata("result", result.toString())
                    }

                    data.forEach { (key, value) ->
                        metadata(key, value.toString())
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn(e) { "Failed to process telemetry event: $telemetryMap" }
        }
    }

    override fun telemetryEvent(`object`: Any) {
        when (`object`) {
            is Map<*, *> -> handleTelemetryMap(`object`)
            else -> LOG.warn { "Unexpected telemetry event: $`object`" }
        }
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

    override fun getConnectionMetadata(): CompletableFuture<ConnectionMetadata> =
        CompletableFuture.supplyAsync {
            val connection = ToolkitConnectionManager.getInstance(project)
                .activeConnectionForFeature(QConnection.getInstance())

            when (connection) {
                is AwsBearerTokenConnection -> {
                    ConnectionMetadata(
                        SsoProfileData(connection.startUrl)
                    )
                }
                else -> {
                    // If no connection or not a bearer token connection return default builderID start url
                    ConnectionMetadata(
                        SsoProfileData(AmazonQLspConstants.AWS_BUILDER_ID_URL)
                    )
                }
            }
        }

    override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> {
        if (params.items.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }

        return CompletableFuture.completedFuture(
            buildList {
                params.items.forEach {
                    when (it.section) {
                        AmazonQLspConstants.LSP_CW_CONFIGURATION_KEY -> {
                            add(
                                CodeWhispererLspConfiguration(
                                    shouldShareData = CodeWhispererSettings.getInstance().isMetricOptIn(),
                                    shouldShareCodeReferences = CodeWhispererSettings.getInstance().isIncludeCodeWithReference(),
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    companion object {
        private val LOG = getLogger<AmazonQLanguageClientImpl>()
    }
}
