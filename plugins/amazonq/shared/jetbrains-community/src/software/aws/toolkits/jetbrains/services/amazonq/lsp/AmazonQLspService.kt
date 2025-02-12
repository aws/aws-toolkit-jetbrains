// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.ToNumberPolicy
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.serialization.Serializable
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.slf4j.event.Level
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Future

@Serializable
data class ExtendedClientMetadata(
    val aws: AwsMetadata,
)

@Serializable
data class AwsMetadata(
    val clientInfo: ClientInfoMetadata,
)

@Serializable
data class ClientInfoMetadata(
    val extension: ExtensionMetadata,
    val clientId: String,
    val version: String,
    val name: String,
)

@Serializable
data class ExtensionMetadata(
    val name: String,
    val version: String,
)

private fun createExtendedClientMetadata(): ExtendedClientMetadata {
    val metadata = ClientMetadata.getDefault()
    return ExtendedClientMetadata(
        aws = AwsMetadata(
            clientInfo = ClientInfoMetadata(
                extension = ExtensionMetadata(
                    name = metadata.awsProduct.toString(),
                    version = metadata.awsVersion
                ),
                clientId = metadata.clientId,
                version = metadata.parentProductVersion,
                name = metadata.parentProduct
            )
        )
    )
}

// https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/server/LSPProcessListener.java
// JB impl and redhat both use a wrapper to handle input buffering issue
internal class LSPProcessListener : ProcessListener {
    private val outputStream = PipedOutputStream()
    private val outputStreamWriter = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
    val inputStream = PipedInputStream(outputStream)

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (ProcessOutputType.isStdout(outputType)) {
            try {
                this.outputStreamWriter.write(event.text)
                this.outputStreamWriter.flush()
            } catch (_: IOException) {
                ExecutionManagerImpl.stopProcess(event.processHandler)
            }
        } else if (ProcessOutputType.isStderr(outputType)) {
            LOG.warn { "LSP process stderr: ${event.text}" }
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        try {
            this.outputStreamWriter.close()
            this.outputStream.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private val LOG = getLogger<LSPProcessListener>()
    }
}

@Service(Service.Level.PROJECT)
class AmazonQLspService(private val project: Project, private val cs: CoroutineScope) : Disposable {
    private val launcher: Launcher<AmazonQLanguageServer>

    private val languageServer: AmazonQLanguageServer
        get() = launcher.remoteProxy

    @Suppress("ForbiddenVoid")
    private val launcherFuture: Future<Void>
    private val launcherHandler: KillableProcessHandler

    private fun createClientCapabilities(): ClientCapabilities {
        return ClientCapabilities().apply {
            textDocument = TextDocumentClientCapabilities().apply {
                // For didSaveTextDocument, other textDocument/ messages always mandatory
                synchronization = SynchronizationCapabilities().apply {
                    didSave = true
                }
            }

            workspace = WorkspaceClientCapabilities().apply {
                applyEdit = false

                // For workspace folder changes
                workspaceFolders = true

                // For file operations (create, delete)
                fileOperations = FileOperationsWorkspaceCapabilities().apply {
                    didCreate = true
                    didDelete = true
                }
            }
        }
    }

    // needs case handling when project's base path is null: default projects/unit tests
    private fun createWorkspaceFolders(): List<WorkspaceFolder> {
        return project.basePath?.let { basePath ->
            listOf(
                WorkspaceFolder(
                    URI("file://$basePath").toString(),
                    project.name
                )
            )
        } ?: emptyList() // no folders to report or workspace not folder based
    }

    private fun createClientInfo(): ClientInfo {
        val metadata = ClientMetadata.getDefault()
        return ClientInfo().apply {
            name = metadata.awsProduct.toString()
            version = metadata.awsVersion
        }
    }

    private fun createInitializeParams(): InitializeParams {
        return InitializeParams().apply {
            processId = ProcessHandle.current().pid().toInt()
            capabilities = createClientCapabilities()
            clientInfo = createClientInfo()
            workspaceFolders = createWorkspaceFolders()
            initializationOptions = createExtendedClientMetadata()
        }
    }

    init {
        val cmd = GeneralCommandLine("amazon-q-lsp")

        launcherHandler = KillableColoredProcessHandler.Silent(cmd)
        val inputWrapper = LSPProcessListener()
        launcherHandler.addProcessListener(inputWrapper)
        launcherHandler.startNotify()

        launcher = LSPLauncher.Builder<AmazonQLanguageServer>()
            .setLocalService(AmazonQLanguageClientImpl())
            .setRemoteInterface(AmazonQLanguageServer::class.java)
            .configureGson {
                // TODO: maybe need adapter for initialize:
                //   https://github.com/aws/amazon-q-eclipse/blob/b9d5bdcd5c38e1dd8ad371d37ab93a16113d7d4b/plugin/src/software/aws/toolkits/eclipse/amazonq/lsp/QLspTypeAdapterFactory.java

                // otherwise Gson treats all numbers as double which causes deser issues
                it.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            }.traceMessages(
                PrintWriter(
                    object : StringWriter() {
                        private val traceLogger = LOG.atLevel(if (isDeveloperMode()) Level.INFO else Level.DEBUG)

                        override fun flush() {
                            traceLogger.log { buffer.toString() }
                            buffer.setLength(0)
                        }
                    }
                )
            )
            .setInput(inputWrapper.inputStream)
            .setOutput(launcherHandler.process.outputStream)
            .create()

        launcherFuture = launcher.startListening()

        cs.launch {
            val initializeResult = try {
                withTimeout(Duration.ofSeconds(30)) {
                    languageServer.initialize(createInitializeParams()).await()
                }
            } catch (e: TimeoutCancellationException) {
                LOG.warn { "LSP initialization timed out" }
                null
            }

            // then if this succeeds then we can allow the client to send requests
            if (initializeResult == null) {
                LOG.warn { "LSP initialization failed" }
                launcherHandler.destroyProcess()
            }
            languageServer.initialized(InitializedParams())
        }
    }

    override fun dispose() {
        if (!launcherFuture.isDone) {
            try {
                languageServer.apply {
                    shutdown().thenRun { exit() }
                }
            } catch (e: Exception) {
                LOG.warn(e) { "LSP shutdown failed" }
                launcherHandler.destroyProcess()
            }
        } else if (!launcherHandler.isProcessTerminated) {
            launcherHandler.destroyProcess()
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQLspService>()
        fun getInstance(project: Project) = project.service<AmazonQLspService>()
    }
}
