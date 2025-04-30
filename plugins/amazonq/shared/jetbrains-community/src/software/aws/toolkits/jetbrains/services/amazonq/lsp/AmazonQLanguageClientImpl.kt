// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.Gson
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import migration.software.aws.toolkits.jetbrains.settings.AwsSettings
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AsyncChatUiListener
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_OPEN_TAB
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

    override fun showDocument(params: ShowDocumentParams?): CompletableFuture<ShowDocumentResult> {
        try {
            if (params == null || params.uri.isNullOrEmpty()) {
                return CompletableFuture.completedFuture(ShowDocumentResult(false))
            }

            ApplicationManager.getApplication().invokeLater {
                try {
                    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(params.uri)
                        ?: throw IllegalArgumentException("Cannot find file: ${params.uri}")

                    FileEditorManager.getInstance(project).openFile(virtualFile, true)
                } catch (e: Exception) {
                    LOG.warn { "Failed to show document: ${params.uri}" }
                }
            }

            return CompletableFuture.completedFuture(ShowDocumentResult(true))
        } catch (e: Exception) {
            LOG.warn { "Error showing document" }
            return CompletableFuture.completedFuture(ShowDocumentResult(false))
        }
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

    override fun openTab(params: OpenTabParams): CompletableFuture<OpenTabResult> {
        val requestId = UUID.randomUUID().toString()
        val result = CompletableFuture<OpenTabResult>()

        pendingTabRequests[requestId] = result

        val uiMessage = """
                {
                "command": "$CHAT_OPEN_TAB",
                "params": ${Gson().toJson(params)},
                "requestId": "$requestId"
                }
        """.trimIndent()
        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)

        result.orTimeout(30000, TimeUnit.MILLISECONDS)
            .whenComplete { _, error ->
                if (error != null) {
                    pendingTabRequests.remove(requestId)
                }
            }

        return result
    }

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
                                    projectContext = ProjectContextConfiguration(
                                        enableLocalIndexing = qSettings.isProjectContextEnabled(),
                                        indexWorkerThreads = qSettings.getProjectContextIndexThreadCount(),
                                        enableGpuAcceleration = qSettings.isProjectContextGpu(),
                                        localIndexing = LocalIndexingConfiguration(
                                            maxIndexSizeMB = qSettings.getProjectContextIndexMaxSize()
                                        )
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

    companion object {
        private val LOG = getLogger<AmazonQLanguageClientImpl>()

        private val pendingTabRequests = ConcurrentHashMap<String, CompletableFuture<OpenTabResult>>()

        fun completeTabOpen(requestId: String, tabId: String) {
            pendingTabRequests.remove(requestId)?.complete(OpenTabResult(tabId))
        }
    }
}
