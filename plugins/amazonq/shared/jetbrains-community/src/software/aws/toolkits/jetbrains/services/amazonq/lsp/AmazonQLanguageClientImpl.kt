// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import migration.software.aws.toolkits.jetbrains.settings.AwsSettings
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.slf4j.event.Level
import software.amazon.awssdk.utils.UserHomeDirectoryUtils
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.ChatCommunicationManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.FlareUiMessage
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.LSPAny
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_OPEN_TAB
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_OPTIONS_UPDATE_NOTIFICATION
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_PINNED_CONTEXT_ADD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_PINNED_CONTEXT_REMOVE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_CONTEXT_COMMANDS
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_PINNED_CONTEXT
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CHAT_SEND_UPDATE
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.CopyFileParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.FileParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GET_SERIALIZED_CHAT_REQUEST_METHOD
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.GetSerializedChatResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.OpenFileDiffParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowOpenFileDialogParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowSaveFileDialogParams
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.chat.ShowSaveFileDialogResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.credentials.ConnectionMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.LspEditorUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.TelemetryParsingUtil
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.applyExtensionFilter
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.utils.getCleanedContent
import software.aws.toolkits.jetbrains.utils.notify
import software.aws.toolkits.resources.message
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Concrete implementation of [AmazonQLanguageClient] to handle messages sent from server
 */
class AmazonQLanguageClientImpl(private val project: Project) : AmazonQLanguageClient {
    private val chatManager
        get() = ChatCommunicationManager.getInstance(project)
    private fun handleTelemetryMap(telemetryMap: Map<*, *>) {
        try {
            val name = telemetryMap["name"] as? String ?: return

            @Suppress("UNCHECKED_CAST")
            val data = telemetryMap["data"] as? Map<String, Any?> ?: return

            TelemetryService.getInstance().record(project) {
                datum(name) {
                    unit(TelemetryParsingUtil.parseMetricUnit(telemetryMap["unit"]))
                    value(telemetryMap["value"] as? Double ?: 1.0)
                    passive(telemetryMap["passive"] as? Boolean ?: false)

                    telemetryMap["result"]?.let { result ->
                        metadata("result", result.toString())
                    }

                    data.forEach { (key, value) ->
                        metadata(key, value?.toString() ?: "null")
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
        notify(
            messageParams.type.toNotificationType(),
            message("toolwindow.stripe.amazon.q.window"),
            getCleanedContent(messageParams.message, true),
            project,
            emptyList()
        )
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem?> {
        val future = CompletableFuture<MessageActionItem?>()
        if (requestParams.actions.isNullOrEmpty()) {
            future.complete(null)
        }

        notify(
            requestParams.type.toNotificationType(),
            message("toolwindow.stripe.amazon.q.window"),
            getCleanedContent(requestParams.message, true),
            project,
            requestParams.actions.map { item ->
                NotificationAction.createSimple(item.title) {
                    future.complete(item)
                }
            }
        )

        return future
    }

    override fun logMessage(message: MessageParams) {
        val type = when (message.type) {
            MessageType.Error -> Level.ERROR
            MessageType.Warning -> Level.WARN
            MessageType.Info, MessageType.Log -> Level.INFO
            else -> Level.WARN
        }

        if (type == Level.ERROR &&
            message.message.lineSequence().firstOrNull()?.contains("NOTE: The AWS SDK for JavaScript (v2) is in maintenance mode.") == true
        ) {
            LOG.info { "Suppressed Flare AWS JS SDK v2 EoL error message" }
            return
        }

        LOG.atLevel(type).log(message.message)
    }

    override fun showDocument(params: ShowDocumentParams): CompletableFuture<ShowDocumentResult> {
        try {
            if (params.uri.isNullOrEmpty()) {
                return CompletableFuture.completedFuture(ShowDocumentResult(false))
            }

            if (params.external == true) {
                BrowserUtil.open(params.uri)
                return CompletableFuture.completedFuture(ShowDocumentResult(true))
            }

            // The filepath sent by the server contains unicode characters which need to be
            // decoded for JB file handling APIs to be handle to handle file operations
            val fileToOpen = URLDecoder.decode(params.uri, StandardCharsets.UTF_8.name())
            return CompletableFuture.supplyAsync(
                {
                    try {
                        val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(fileToOpen)
                            ?: throw IllegalArgumentException("Cannot find file: $fileToOpen")

                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                        ShowDocumentResult(true)
                    } catch (e: Exception) {
                        LOG.warn { "Failed to show document: $fileToOpen" }
                        ShowDocumentResult(false)
                    }
                },
                ApplicationManager.getApplication()::invokeLater
            )
        } catch (e: Exception) {
            LOG.warn { "Error showing document" }
            return CompletableFuture.completedFuture(ShowDocumentResult(false))
        }
    }

    override fun getConnectionMetadata(): CompletableFuture<ConnectionMetadata> =
        CompletableFuture.supplyAsync {
            val connection = ToolkitConnectionManager.getInstance(project)
                .activeConnectionForFeature(QConnection.getInstance())

            connection?.let { ConnectionMetadata.fromConnection(it) }
        }

    override fun openTab(params: LSPAny): CompletableFuture<LSPAny> {
        val requestId = UUID.randomUUID().toString()
        val result = CompletableFuture<LSPAny>()
        chatManager.addTabOpenRequest(requestId, result)

        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_OPEN_TAB,
                params = params,
                requestId = requestId,
            )
        )

        result.orTimeout(30000, TimeUnit.MILLISECONDS)
            .whenComplete { _, error ->
                chatManager.removeTabOpenRequest(requestId)
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
                } ?: throw ResponseErrorException(ResponseError(ResponseErrorCode.RequestCancelled, "Export cancelled by user", null))
            },
            ApplicationManager.getApplication()::invokeLater
        )
    }

    override fun showOpenFileDialog(params: ShowOpenFileDialogParams): CompletableFuture<LSPAny> =
        CompletableFuture.supplyAsync(
            {
                // Handle the case where both canSelectFiles and canSelectFolders are false (should never be sent from flare)
                if (!params.canSelectFiles && !params.canSelectFolders) {
                    return@supplyAsync mapOf("uris" to emptyList<String>()) as LSPAny
                }

                val descriptor = when {
                    params.canSelectFolders && params.canSelectFiles -> {
                        if (params.canSelectMany) {
                            FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
                        } else {
                            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
                        }
                    }
                    params.canSelectFolders -> {
                        if (params.canSelectMany) {
                            FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
                        } else {
                            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        }
                    }
                    else -> {
                        if (params.canSelectMany) {
                            FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
                        } else {
                            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                        }
                    }
                }.apply {
                    withTitle(
                        params.title ?: when {
                            params.canSelectFolders && params.canSelectFiles -> "Select Files or Folders"
                            params.canSelectFolders -> "Select Folders"
                            else -> "Select Files"
                        }
                    )
                    withDescription(
                        when {
                            params.canSelectFolders && params.canSelectFiles -> "Choose files or folders to open"
                            params.canSelectFolders -> "Choose folders to open"
                            else -> "Choose files to open"
                        }
                    )

                    // Apply file filters if provided
                    if (params.filters.isNotEmpty() && !params.canSelectFolders) {
                        // Create a combined list of all allowed extensions
                        val allowedExtensions = params.filters.values.flatten().toSet()
                        applyExtensionFilter(this, "Images", allowedExtensions)
                    }
                }

                val chosenFiles = FileChooser.chooseFiles(descriptor, project, null)
                val uris = chosenFiles.map { it.path }

                mapOf("uris" to uris) as LSPAny
            },
            ApplicationManager.getApplication()::invokeLater
        )

    override fun getSerializedChat(params: LSPAny): CompletableFuture<GetSerializedChatResult> {
        val requestId = UUID.randomUUID().toString()
        val result = CompletableFuture<GetSerializedChatResult>()
        chatManager.addSerializedChatRequest(requestId, result)

        chatManager.notifyUi(
            FlareUiMessage(
                command = GET_SERIALIZED_CHAT_REQUEST_METHOD,
                params = params,
                requestId = requestId,
            )
        )

        result.orTimeout(30000, TimeUnit.MILLISECONDS)
            .whenComplete { _, error ->
                chatManager.removeSerializedChatRequest(requestId)
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
                                    optOutTelemetry = !AwsSettings.getInstance().isTelemetryEnabled,
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
        try {
            chatManager.handlePartialResultProgressNotification(project, params)
        } catch (e: Exception) {
            LOG.error(e) { "Cannot handle partial chat" }
        }
    }

    override fun sendChatUpdate(params: LSPAny) {
        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_SEND_UPDATE,
                params = params,
            )
        )
    }

    private fun File.toVirtualFile() = LocalFileSystem.getInstance().findFileByIoFile(this)

    private fun MessageType.toNotificationType() = when (this) {
        MessageType.Error -> NotificationType.ERROR
        MessageType.Warning -> NotificationType.WARNING
        MessageType.Info, MessageType.Log -> NotificationType.INFORMATION
    }

    override fun openFileDiff(params: OpenFileDiffParams) {
        ApplicationManager.getApplication().invokeLater {
            var tempPath: java.nio.file.Path? = null
            try {
                val fileName = Paths.get(params.originalFileUri).fileName.toString()
                // Create a temporary virtual file for syntax highlighting
                val fileExtension = fileName.substringAfterLast('.', "")
                tempPath = Files.createTempFile(null, ".$fileExtension")
                val virtualFile = tempPath.toFile()
                    .also { it.setReadOnly() }
                    .toVirtualFile()

                val originalContent = params.originalFileContent ?: run {
                    val sourceFile = File(params.originalFileUri)
                    if (sourceFile.exists()) sourceFile.readText() else ""
                }

                val contentFactory = DiffContentFactory.getInstance()
                var isNewFile = false
                val (leftContent, rightContent) = when {
                    params.isDeleted -> {
                        contentFactory.create(project, originalContent, virtualFile) to
                            contentFactory.createEmpty()
                    }

                    else -> {
                        val newContent = params.fileContent.orEmpty()
                        isNewFile = newContent == originalContent
                        when {
                            isNewFile -> {
                                contentFactory.createEmpty() to
                                    contentFactory.create(project, newContent, virtualFile)
                            }

                            else -> {
                                contentFactory.create(project, originalContent, virtualFile) to
                                    contentFactory.create(project, newContent, virtualFile)
                            }
                        }
                    }
                }
                val diffRequest = SimpleDiffRequest(
                    "$fileName ${message("aws.q.lsp.client.diff_message")}",
                    leftContent,
                    rightContent,
                    "Original",
                    when {
                        params.isDeleted -> "Deleted"
                        isNewFile -> "Created"
                        else -> "Modified"
                    }
                )

                AmazonQDiffVirtualFile.openDiff(project, diffRequest)
            } catch (e: Exception) {
                LOG.warn { "Failed to open file diff: ${e.message}" }
            } finally {
                // Clean up the temporary file used for syntax highlight
                try {
                    tempPath?.let { Files.deleteIfExists(it) }
                } catch (e: Exception) {
                    LOG.warn { "Failed to delete temporary file: ${e.message}" }
                }
            }
        }
    }

    override fun sendContextCommands(params: LSPAny) {
        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_SEND_CONTEXT_COMMANDS,
                params = params,
            )
        )
    }

    override fun sendPinnedContext(params: LSPAny) {
        // Send the active text file path with pinned context
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val textDocument = editor?.let {
            val relativePath = VfsUtilCore.getRelativePath(it.virtualFile, project.baseDir)
                ?: it.virtualFile.path // Use absolute path if not in project
            TextDocumentIdentifier(relativePath)
        }

        // Create updated params with text document information
        // Since params is LSPAny, we need to handle it as a generic object
        val updatedParams = when (params) {
            is Map<*, *> -> {
                val mutableParams = params.toMutableMap()
                mutableParams["textDocument"] = textDocument
                mutableParams
            }
            else -> mapOf(
                "params" to params,
                "textDocument" to textDocument
            )
        }

        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_SEND_PINNED_CONTEXT,
                params = updatedParams,
            )
        )
    }

    override fun pinnedContextAdd(params: LSPAny) {
        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_PINNED_CONTEXT_ADD,
                params = params,
            )
        )
    }

    override fun pinnedContextRemove(params: LSPAny) {
        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_PINNED_CONTEXT_REMOVE,
                params = params,
            )
        )
    }

    override fun appendFile(params: FileParams) = refreshVfs(params.path)

    override fun createDirectory(params: FileParams) = refreshVfs(params.path)

    override fun removeFile(params: FileParams) = refreshVfs(params.path)

    override fun writeFile(params: FileParams) = refreshVfs(params.path)

    override fun copyFile(params: CopyFileParams) {
        refreshVfs(params.oldPath)
        return refreshVfs(params.newPath)
    }

    override fun sendChatOptionsUpdate(params: LSPAny) {
        chatManager.notifyUi(
            FlareUiMessage(
                command = CHAT_OPTIONS_UPDATE_NOTIFICATION,
                params = params,
            )
        )
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> =
        CompletableFuture.supplyAsync(
            {
                try {
                    LspEditorUtil.applyWorkspaceEdit(project, params.edit)
                    ApplyWorkspaceEditResponse(true)
                } catch (e: Exception) {
                    LOG.warn(e) { "Failed to apply workspace edit" }
                    ApplyWorkspaceEditResponse(false)
                }
            },
            ApplicationManager.getApplication()::invokeLater
        )

    private fun refreshVfs(path: String) {
        val currPath = Paths.get(path)
        if (currPath.startsWith(localHistoryPath)) return
        try {
            ApplicationManager.getApplication().executeOnPooledThread {
                VfsUtil.markDirtyAndRefresh(false, true, true, currPath.toFile())
            }
        } catch (e: Exception) {
            LOG.warn(e) { "Could not refresh file" }
        }
    }

    companion object {
        val localHistoryPath = Paths.get(
            UserHomeDirectoryUtils.userHomeDirectory(),
            ".aws",
            "amazonq",
            "history"
        )
        private val LOG = getLogger<AmazonQLanguageClientImpl>()
    }
}
