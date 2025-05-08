// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.Gson
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
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
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LSPAny
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_OPEN_TAB
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_CONTEXT_COMMANDS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_UPDATE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ChatUpdateParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GET_SERIALIZED_CHAT_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenFileDiffParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenTabResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowSaveFileDialogParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowSaveFileDialogResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.SsoProfileData
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.resources.message
import java.io.File
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
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
        ChatCommunicationManager.pendingTabRequests[requestId] = result

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
                ChatCommunicationManager.pendingTabRequests.remove(requestId)
            }

        return result
    }

    override fun showSaveFileDialog(params: ShowSaveFileDialogParams): CompletableFuture<ShowSaveFileDialogResult> {
        val filters = mutableListOf<String>()
        val formatMappings = mapOf("markdown" to "md", "html" to "html")

        params.supportedFormats.forEach { format ->
            formatMappings[format]?.let { filters.add(it) }
        }
        val defaultUri = params.defaultUri ?: "export-chat.md"
        val saveAtUri = defaultUri.substring(defaultUri.lastIndexOf("/") + 1)
        return CompletableFuture.supplyAsync(
            {
                val descriptor = FileSaverDescriptor("Export", "Choose a location to export").apply {
                    withFileFilter { file ->
                        filters.any { ext ->
                            file.name.endsWith(".$ext")
                        }
                    }
                }

                val chosenFile = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).save(saveAtUri)

                chosenFile?.let {
                    ShowSaveFileDialogResult(chosenFile.file.path)
                    // TODO: Add error state shown in chat ui instead of throwing
                } ?: throw Error("Export failed")
            },
            ApplicationManager.getApplication()::invokeLater
        )
    }

    override fun getSerializedChat(params: GetSerializedChatParams): CompletableFuture<GetSerializedChatResult> {
        val requestId = UUID.randomUUID().toString()
        val result = CompletableFuture<GetSerializedChatResult>()

        ChatCommunicationManager.pendingSerializedChatRequests[requestId] = result

        val uiMessage = """
                {
                "command": "$GET_SERIALIZED_CHAT_REQUEST_METHOD",
                "params": ${Gson().toJson(params)},
                "requestId": "$requestId"
                }
        """.trimIndent()
        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)

        result.orTimeout(30000, TimeUnit.MILLISECONDS)
            .whenComplete { _, error ->
                ChatCommunicationManager.pendingSerializedChatRequests.remove(requestId)
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

    override fun sendChatUpdate(params: ChatUpdateParams): CompletableFuture<Unit> {
        val uiMessage = """
        {
        "command":"$CHAT_SEND_UPDATE",
        "params":${Gson().toJson(params)}
        }
        """.trimIndent()

        AsyncChatUiListener.notifyPartialMessageUpdate(uiMessage)

        return CompletableFuture.completedFuture(Unit)
    }

    override fun openFileDiff(params: OpenFileDiffParams): CompletableFuture<Unit> =
        CompletableFuture.supplyAsync(
            {
                try {
                    val contentFactory = DiffContentFactory.getInstance()
                    val fileName = Paths.get(params.originalFileUri).fileName.toString()

                    val originalContent = params.originalFileContent ?: run {
                        val file = File(params.originalFileUri)
                        if (file.exists()) file.readText() else ""
                    }
                    val (leftContent, rightContent) = when {
                        params.isDeleted -> {
                            // For deleted files, show original on left, empty on right
                            contentFactory.create(originalContent) to
                                contentFactory.createEmpty()
                        }
                        else -> {
                            // For new or modified files
                            val newContent = params.fileContent.orEmpty()
                            contentFactory.create(originalContent) to
                                contentFactory.create(newContent)
                        }
                    }
                    val diffRequest = SimpleDiffRequest(
                        "$fileName ${message("aws.q.lsp.client.diff_message")}",
                        leftContent,
                        rightContent,
                        "Original",
                        if (params.isDeleted) "Deleted" else "Modified"
                    )
                    DiffManager.getInstance().showDiff(project, diffRequest)
                } catch (e: Exception) {
                    LOG.warn { "Failed to open file diff: ${e.message}" }
                }
            },
            ApplicationManager.getApplication()::invokeLater
        )

    override fun sendContextCommands(params: LSPAny): CompletableFuture<Unit> {
        val showContextCommands = """
            {
            "command":"$CHAT_SEND_CONTEXT_COMMANDS",
            "params": ${Gson().toJson(params)}
            }
        """.trimIndent()

        AsyncChatUiListener.notifyPartialMessageUpdate(showContextCommands)

        return CompletableFuture.completedFuture(Unit)
    }

    companion object {
        private val LOG = getLogger<AmazonQLanguageClientImpl>()
    }
}
