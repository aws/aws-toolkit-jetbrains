// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("BannedImports")
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
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.await
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.JdkProxyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.apache.http.client.utils.URIBuilder
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.FileOperationsWorkspaceCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.SynchronizationCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.launch.LSPLauncher
import org.slf4j.event.Level
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.core.utils.writeText
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts.ArtifactManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.auth.DefaultAuthCredentialsService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.dependencies.DefaultModuleDependenciesService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption.JwtEncryptionManager
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AmazonQLspTypeAdapterFactory
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsExtendedInitializeResult
import software.aws.toolkits.jetbrains.services.amazonq.lsp.flareChat.AwsServerCapabilitiesProvider
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.createExtendedClientMetadata
import software.aws.toolkits.jetbrains.services.amazonq.lsp.textdocument.TextDocumentServiceHandler
import software.aws.toolkits.jetbrains.services.amazonq.lsp.util.WorkspaceFolderUtil.createWorkspaceFolders
import software.aws.toolkits.jetbrains.services.amazonq.lsp.workspace.WorkspaceServiceHandler
import software.aws.toolkits.jetbrains.services.amazonq.profile.QDefaultServiceConfig
import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata
import software.aws.toolkits.jetbrains.settings.LspSettings
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.Future
import kotlin.time.Duration.Companion.seconds

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
    private val _flowInstance = MutableSharedFlow<AmazonQServerInstance>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val instanceFlow = _flowInstance.asSharedFlow().map { it.languageServer }

    private var instance: Deferred<AmazonQServerInstance>
    val capabilities
        get() = instance.getCompleted().initializeResult.getCompleted().capabilities
    val encryptionManager
        get() = instance.getCompleted().encryptionManager
    private val heartbeatJob: Job

    // dont allow lsp commands if server is restarting
    private val mutex = Mutex(false)

    private fun start() = cs.async {
        // manage lifecycle RAII-like so we can restart at arbitrary time
        // and suppress IDE error if server fails to start
        var attempts = 0
        while (attempts < 3) {
            try {
                return@async withTimeout(30.seconds) {
                    val instance = AmazonQServerInstance(project, cs).also {
                        Disposer.register(this@AmazonQLspService, it)
                    }
                    // wait for handshake to complete
                    instance.initializeResult.join()

                    instance.also {
                        _flowInstance.emit(it)
                    }
                }
            } catch (e: Exception) {
                LOG.warn(e) { "Failed to start LSP server" }
            }
            attempts++
        }

        error("Failed to start LSP server in 3 attempts")
    }

    init {
        instance = start()

        // Initialize heartbeat job
        heartbeatJob = cs.launch {
            while (isActive) {
                delay(5.seconds) // Check every 5 seconds
                checkConnectionStatus()
            }
        }
    }

    private suspend fun checkConnectionStatus() {
        try {
            val currentInstance = mutex.withLock { instance }.await()

            // Check if the launcher's Future (startListening) is done
            // If it's done, that means the connection has been terminated
            if (currentInstance.launcherFuture.isDone) {
                LOG.debug { "LSP server connection terminated, restarting server" }
                restart()
            } else {
                LOG.debug { "LSP server is currently running " }
            }
        } catch (e: Exception) {
            LOG.debug(e) { "Connection status check failed, restarting LSP server" }
            restart()
        }
    }

    override fun dispose() {
        heartbeatJob.cancel()
    }

    suspend fun restart() = mutex.withLock {
        // stop if running
        instance.let {
            if (it.isActive) {
                // not even running yet
                return
            }

            try {
                val i = it.await()
                if (i.initializeResult.isActive) {
                    // not initialized
                    return
                }

                Disposer.dispose(i)
            } catch (e: Exception) {
                LOG.info(e) { "Exception while disposing LSP server" }
            }
        }

        instance = start()
    }

    suspend fun<T> execute(runnable: suspend AmazonQLspService.(AmazonQLanguageServer) -> T): T {
        val lsp = withTimeout(10.seconds) {
            val holder = mutex.withLock { instance }.await()
            holder.initializeResult.join()

            holder.languageServer
        }
        return runnable(lsp)
    }

    fun<T> executeSync(runnable: suspend AmazonQLspService.(AmazonQLanguageServer) -> T): T =
        runBlocking(cs.coroutineContext) {
            execute(runnable)
        }

    companion object {
        private val LOG = getLogger<AmazonQLspService>()
        fun getInstance(project: Project) = project.service<AmazonQLspService>()

        fun <T> executeIfRunning(project: Project, runnable: AmazonQLspService.(AmazonQLanguageServer) -> T): T? =
            project.serviceIfCreated<AmazonQLspService>()?.executeSync(runnable)

        fun didChangeConfiguration(project: Project) {
            executeIfRunning(project) {
                it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams())
            }
        }
    }
}

private class AmazonQServerInstance(private val project: Project, private val cs: CoroutineScope) : Disposable {
    val encryptionManager = JwtEncryptionManager()

    private val launcher: Launcher<AmazonQLanguageServer>

    val languageServer: AmazonQLanguageServer
        get() = launcher.remoteProxy

    @Suppress("ForbiddenVoid")
    var launcherFuture: Future<Void>
        private set
    private val launcherHandler: KillableProcessHandler
    val initializeResult: Deferred<InitializeResult>

    private fun createClientCapabilities(): ClientCapabilities =
        ClientCapabilities().apply {
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
                    didRename = true
                }
            }
        }

    private fun createClientInfo(): ClientInfo {
        val metadata = ClientMetadata.getDefault()
        return ClientInfo().apply {
            name = metadata.awsProduct.toString()
            version = metadata.awsVersion
        }
    }

    private fun createInitializeParams(): InitializeParams =
        InitializeParams().apply {
            processId = ProcessHandle.current().pid().toInt()
            capabilities = createClientCapabilities()
            clientInfo = createClientInfo()
            workspaceFolders = createWorkspaceFolders(project)
            initializationOptions = createExtendedClientMetadata()
        }

    init {
        // will cause slow service init, but maybe fine for now. will not block UI since fetch/extract will be under background progress
        val artifact = runBlocking { service<ArtifactManager>().fetchArtifact(project) }.toAbsolutePath()

        // more network calls
        // make assumption that all requests will resolve to the same CA
        // also terrible assumption that default endpoint is reachable
        val qUri = URI(QDefaultServiceConfig.ENDPOINT)
        val rtsTrustChain = TrustChainUtil.getTrustChain(qUri)
        val extraCaCerts = Files.createTempFile("q-extra-ca", ".pem").apply {
            writeText(
                TrustChainUtil.certsToPem(rtsTrustChain)
            )
        }

        val node = if (SystemInfo.isWindows) "node.exe" else "node"
        val cmd = GeneralCommandLine(
            artifact.resolve(node).toString(),
            LspSettings.getInstance().getArtifactPath() ?: artifact.resolve("aws-lsp-codewhisperer.js").toString(),
            "--stdio",
            "--set-credentials-encryption-key",
        ).withEnvironment(
            buildMap {
                put("NODE_EXTRA_CA_CERTS", extraCaCerts.toAbsolutePath().toString())

                val proxy = JdkProxyProvider.getInstance().proxySelector.select(qUri)
                    // log if only socks proxy available
                    .firstOrNull { it.type() == Proxy.Type.HTTP }

                if (proxy != null) {
                    val address = proxy.address()
                    if (address is java.net.InetSocketAddress) {
                        put(
                            "HTTPS_PROXY",
                            URIBuilder("http://${address.hostName}:${address.port}").apply {
                                val login = HttpConfigurable.getInstance().proxyLogin
                                if (login != null) {
                                    setUserInfo(login, HttpConfigurable.getInstance().plainProxyPassword)
                                }
                            }.build().toASCIIString()
                        )
                    }
                }
            }
        )
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        launcherHandler = KillableColoredProcessHandler.Silent(cmd)
        val inputWrapper = LSPProcessListener()
        launcherHandler.addProcessListener(inputWrapper)
        launcherHandler.startNotify()

        launcher = LSPLauncher.Builder<AmazonQLanguageServer>()
            .wrapMessages { consumer ->
                MessageConsumer { message ->
                    if (message is ResponseMessage && message.result is AwsExtendedInitializeResult) {
                        val result = message.result as AwsExtendedInitializeResult
                        AwsServerCapabilitiesProvider.getInstance(project).setAwsServerCapabilities(result.getAwsServerCapabilities())
                    }
                    consumer?.consume(message)
                }
            }
            .setLocalService(AmazonQLanguageClientImpl(project))
            .setRemoteInterface(AmazonQLanguageServer::class.java)
            .configureGson {
                // TODO: maybe need adapter for initialize:
                //   https://github.com/aws/amazon-q-eclipse/blob/b9d5bdcd5c38e1dd8ad371d37ab93a16113d7d4b/plugin/src/software/aws/toolkits/eclipse/amazonq/lsp/QLspTypeAdapterFactory.java

                // otherwise Gson treats all numbers as double which causes deser issues
                it.setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                it.registerTypeAdapterFactory(AmazonQLspTypeAdapterFactory())
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

        initializeResult = cs.async {
            // encryption info must be sent within 5s or Flare process will exit
            encryptionManager.writeInitializationPayload(launcherHandler.process.outputStream)

            val initializeResult = try {
                languageServer.initialize(createInitializeParams()).await()
            } catch (e: Exception) {
                LOG.warn(e) { "LSP initialization failed" }
                null
            }

            // then if this succeeds then we can allow the client to send requests
            if (initializeResult == null) {
                launcherHandler.destroyProcess()
                error("LSP initialization failed")
            }
            languageServer.initialized(InitializedParams())

            initializeResult
        }

        // invokeOnCompletion results in weird lock/timeout error
        initializeResult.asCompletableFuture().handleAsync { lspInitResult, ex ->
            if (ex != null) {
                return@handleAsync
            }

            this@AmazonQServerInstance.apply {
                DefaultAuthCredentialsService(project, encryptionManager, this)
                TextDocumentServiceHandler(project, this)
                WorkspaceServiceHandler(project, lspInitResult, this)
                DefaultModuleDependenciesService(project, this)
            }
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
        private val LOG = getLogger<AmazonQServerInstance>()
    }
}

typealias AmazonQInitializeMessageReceivedListener = () -> Unit
