// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import migration.software.aws.toolkits.jetbrains.settings.AwsSettings
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatUpdateParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.SEND_CHAT_COMMAND_PROMPT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.util.concurrent.CompletableFuture

/**
 * Concrete implementation of [AmazonQLanguageClient] to handle messages sent from server
 */
class AmazonQLanguageClientImpl(private val project: Project) : AmazonQLanguageClient {
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

    override fun openTab(params: OpenTabParams): CompletableFuture<OpenTabResult> =
        // TODO implement chat history, this is here to unblock chat functionality
        CompletableFuture.completedFuture(OpenTabResult(""))

    override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> {
        if (params.items.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }

        return CompletableFuture.completedFuture(
            buildList {
                val qSettings = CodeWhispererSettings.getInstance()
                params.items.forEach {
                    when (it.section) {
                        AmazonQLspConstants.LSP_CW_CONFIGURATION_KEY -> {
                            add(
                                CodeWhispererLspConfiguration(
                                    shouldShareData = qSettings.isMetricOptIn(),
                                    shouldShareCodeReferences = qSettings.isIncludeCodeWithReference(),
                                    // server context
                                    shouldEnableWorkspaceContext = qSettings.isWorkspaceContextEnabled()
                                )
                            )
                        }
                        AmazonQLspConstants.LSP_Q_CONFIGURATION_KEY -> {
                            add(
                                AmazonQLspConfiguration(
                                    optOutTelemetry = AwsSettings.getInstance().isTelemetryEnabled,
                                    customization = CodeWhispererModelConfigurator.getInstance().activeCustomization(project)?.arn,
                                    // local context
                                    enableLocalIndexing = qSettings.isProjectContextEnabled(),
                                    indexWorkerThreads = qSettings.getProjectContextIndexThreadCount(),
                                    enableGpuAcceleration = qSettings.isProjectContextGpu(),
                                    localIndexing = LocalIndexingConfiguration(
                                        maxIndexSizeMB = qSettings.getProjectContextIndexMaxSize()
                                    )
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    override fun notifyProgress(params: ProgressParams?) {
        if (params == null) return
        val chatCommunicationManager = ChatCommunicationManager.getInstance(project)
        try {
            chatCommunicationManager.handlePartialResultProgressNotification(project, params)
        } catch (e: Exception) {
            error("Cannot handle partial chat")
        }
    }

    override fun sendChatUpdate(params: ChatUpdateParams): CompletableFuture<Unit> {
        // Process the chat update notification from the server
        // This notification is used to add or update messages in a specific tab
        val tabId = params.tabId
        val state = params.state
        val data = params.data

        val encryptionManager = AmazonQLspService.getInstance(project).encryptionManager

        val uiMessage = ChatCommunicationManager.convertToJsonToSendToChat(
            command = SEND_CHAT_COMMAND_PROMPT,
            tabId = tabId,
            params = encryptionManager.decrypt(params.toString()),
            isPartialResult = false
        )

        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)
        
        return CompletableFuture.completedFuture(Unit)
    }
}
